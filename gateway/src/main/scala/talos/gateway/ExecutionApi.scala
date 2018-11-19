package talos.gateway

import akka.actor.ActorSystem
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.pattern.CircuitBreaker
import akka.stream.javadsl.SourceQueueWithComplete
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{Keep, Sink, Source}
import cats.effect.IO
import com.typesafe.config.ConfigFactory
import talos.circuitbreakers.TalosCircuitBreaker
import talos.circuitbreakers.akka._
import talos.gateway.Gateway.{HttpCall, ServiceCall}
import talos.gateway.config.GatewayConfig

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

import akka.http.scaladsl.model._


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
  private type QUEUE = SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])]
  def http(gatewayConfig: GatewayConfig)(implicit actorSystem: ActorSystem) = {
    implicit val actorMaterializer = ActorMaterializer()
    val executionContexts: Map[String, QUEUE] = {
      val fromServices = gatewayConfig.services.map {
        service =>
           val bulkeadingConfigString = s"""
            akka.http.host-connection-pool {
            min-connections = ${service.maxInflightRequests / 4},
            max-connections = ${service.maxInflightRequests},
            max-open-requests = ${service.maxInflightRequests * 2},
            }
          """
          val QueueSize = service.maxInflightRequests * 4

          val bulkeadingConfig =
            actorSystem.settings.config.resolveWith(ConfigFactory.parseString(bulkeadingConfigString))
          service.host -> ConnectionPoolSettings(bulkeadingConfig)
          val poolClientFlow = Http().cachedHostConnectionPool[Promise[HttpResponse]](service.host, service.port)

          val queue =
            Source.queue[(HttpRequest, Promise[HttpResponse])](QueueSize, OverflowStrategy.dropNew)
              .via(poolClientFlow)
              .toMat(Sink.foreach({
                case ((Success(resp), p)) => p.success(resp)
                case ((Failure(e), p))    => p.failure(e)
              }))(Keep.left).run()

          service.host -> queue
      }
      fromServices.toMap
    }

    def queueRequest(request: HttpRequest, queue: QUEUE): Future[HttpResponse] = {
      val responsePromise = Promise[HttpResponse]()
      queue.offer(request -> responsePromise).flatMap {
        case QueueOfferResult.Enqueued    => responsePromise.future
        case QueueOfferResult.Dropped     => Future.failed(new RuntimeException("Queue overflowed. Try again later."))
        case QueueOfferResult.Failure(ex) => Future.failed(ex)
        case QueueOfferResult.QueueClosed => Future.failed(new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later."))
      }
    }

    apply(
      gatewayConfig,
      serviceCall => IO.fromFuture {
        IO {
          val queue = executionContexts(serviceCall.hitEndpoint.service)
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