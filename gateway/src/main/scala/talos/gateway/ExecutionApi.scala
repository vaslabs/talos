package talos.gateway

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.pattern.CircuitBreaker
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import cats.effect.IO
import com.typesafe.config.{Config, ConfigFactory}
import talos.circuitbreakers.TalosCircuitBreaker
import talos.circuitbreakers.akka._
import talos.gateway.Gateway.{HttpCall, ServiceCall}
import talos.gateway.config.GatewayConfig

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}


trait ExecutionApi[F[_]] {
  def executeCall(httpCommand: HttpCall): F[HttpResponse]
}

class ServiceExecutionApi private[gateway](gatewayConfig: GatewayConfig, httpExecution: ServiceCall => IO[HttpResponse])(implicit actorSystem: ActorSystem) extends ExecutionApi[Future] {



  lazy val circuitBreakers: Map[String, TalosCircuitBreaker[CircuitBreaker, IO]] = {
    gatewayConfig.services.map {
      serviceConfig =>
        val circuitBreaker = CircuitBreaker(
          actorSystem.scheduler,
          serviceConfig.importance.consecutiveFailuresThreshold,
          serviceConfig.callTimeout,
          serviceConfig.importance.resetTimeout
        )
        serviceConfig.host -> AkkaCircuitBreaker(serviceConfig.host, circuitBreaker)
    }.toMap
  }

  override def executeCall(httpCommand: HttpCall): Future[HttpResponse] = {
    val executeHttpRequest = httpCommand match {
      case Gateway.ServiceCall(hitEndpoint, request) =>
        val unprotectedResponse = for {
          httpRequest <- EndpointResolver.transformRequest(request, hitEndpoint)
          unprotectedResponse <- httpExecution(ServiceCall(hitEndpoint, httpRequest))
        } yield unprotectedResponse
        val circuitBreaker = circuitBreakers(hitEndpoint.service)
        circuitBreaker.protect(unprotectedResponse)
    }
    executeHttpRequest.unsafeToFuture()
  }
}

object ExecutionApi {

  private def hostConnectionPoolConfig(maxInflightRequests: Int): Config =
    ConfigFactory.parseString(s"""
            akka.http.host-connection-pool {
            min-connections = ${maxInflightRequests / 4},
            max-connections = ${maxInflightRequests},
            max-open-requests = ${maxInflightRequests * 2},
            }
    """)

  private type QUEUE = SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])]
  private type FLOW_INPUT = (HttpRequest, Promise[HttpResponse])
  private type FLOW_OUTPUT = (Try[HttpResponse], Promise[HttpResponse])
  private type FLOW = Flow[FLOW_INPUT, FLOW_OUTPUT, Http.HostConnectionPool]

  private[this] def queue(poolClientFlow: FLOW, size: Int)(implicit materializer: ActorMaterializer) =
    Source.queue[(HttpRequest, Promise[HttpResponse])](size, OverflowStrategy.dropNew)
      .via(poolClientFlow)
      .toMat(Sink.foreach({
        case ((Success(resp), p)) => p.success(resp)
        case ((Failure(e), p))    => p.failure(e)
      }))(Keep.left).run()

  def http(gatewayConfig: GatewayConfig)(implicit actorSystem: ActorSystem) = {
    implicit val actorMaterializer = ActorMaterializer()
    val queuesPerService: Map[String, QUEUE] = {
      val fromServices = gatewayConfig.services.map {
        service =>

          val bulkeadingConfig =
            actorSystem.settings.config.resolveWith(hostConnectionPoolConfig(service.maxInflightRequests))

          val poolClientFlow = Http().cachedHostConnectionPool[Promise[HttpResponse]](
            service.host,
            service.port,
            ConnectionPoolSettings(bulkeadingConfig)
          )

          val queueSize = service.maxInflightRequests * 4

          service.host -> queue(poolClientFlow, queueSize)
      }
      fromServices.toMap
    }

    def resolvePromise(promise: Promise[HttpResponse], queueOfferResult: QueueOfferResult) =
    queueOfferResult match {
      case QueueOfferResult.Enqueued    => promise.future
      case QueueOfferResult.Dropped     => Future.failed(new RuntimeException("Queue overflowed. Try again later."))
      case QueueOfferResult.Failure(ex) => Future.failed(ex)
      case QueueOfferResult.QueueClosed => Future.failed(new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later."))
    }


    def queueRequest(request: HttpRequest, queue: QUEUE): Future[HttpResponse] = {
      import actorSystem.dispatcher
      val responsePromise = Promise[HttpResponse]()
      queue.offer(request -> responsePromise).flatMap(resolvePromise(responsePromise, _))
    }

    apply(
      gatewayConfig,
      serviceCall => IO.fromFuture {
        IO {
          val queue = queuesPerService(serviceCall.hitEndpoint.service)
          queueRequest(serviceCall.request, queue)
        }
      }
    )
  }

  private[gateway] def apply
      (gatewayConfig: GatewayConfig, httpRequest: ServiceCall => IO[HttpResponse])
      (implicit actorSystem: ActorSystem) =
    new ServiceExecutionApi(gatewayConfig, httpRequest)
}