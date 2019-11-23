package talos.gateway

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.pattern.CircuitBreaker
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}
import cats.effect.IO
import talos.circuitbreakers.akka._
import talos.gateway.EndpointResolver.HitEndpoint
import talos.gateway.Gateway.{HttpCall, ServiceCall}
import talos.gateway.config.{GatewayConfig, ServiceConfig}

import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._


trait ExecutionApi[F[_]] {
  def executeCall(httpCommand: HttpCall): F[HttpResponse]
}

class ServiceExecutionApi[T] private[gateway](
                          gatewayConfig: GatewayConfig, httpExecution: ServiceCall => IO[HttpResponse])(implicit
                          actorSystem: ActorSystem[_],
                          classTag: ClassTag[T]) extends ExecutionApi[Future] {


  lazy val circuitBreakers: Map[String, AkkaCircuitBreaker.Instance[T]] = {
    gatewayConfig.services.map {
      serviceConfig =>
        val circuitBreaker = CircuitBreaker(
          actorSystem.scheduler.toClassic,
          serviceConfig.importance.consecutiveFailuresThreshold,
          serviceConfig.callTimeout,
          serviceConfig.importance.resetTimeout
        )
        ExecutionApi.resolveServiceName(serviceConfig) -> AkkaCircuitBreaker[T](serviceConfig.host, circuitBreaker)
    }.toMap
  }

  override def executeCall(httpCommand: HttpCall): Future[HttpResponse] = {
    val executeHttpRequest = httpCommand match {
      case Gateway.ServiceCall(hitEndpoint, request) =>
        val unprotectedResponse = for {
          httpRequest <- EndpointResolver.transformRequest(request, hitEndpoint)
          unprotectedResponse <- httpExecution(ServiceCall(hitEndpoint, httpRequest))
        } yield unprotectedResponse
        val circuitBreaker = circuitBreakers(ExecutionApi.resolveServiceName(hitEndpoint))
        circuitBreaker.protect(unprotectedResponse)
    }
    executeHttpRequest.handleErrorWith { err =>
      IO.raiseError(err)
    }.unsafeToFuture()
  }
}

object ExecutionApi {

  def resolveServiceName(service: ServiceConfig) =
    s"${service.host}:${service.port}"
  def resolveServiceName(hitEndpoint: HitEndpoint) =
    s"${hitEndpoint.service}:${hitEndpoint.port}"


  private def hostConnectionPoolConfig(maxInflightRequests: Int)(implicit actorSystem: ActorSystem[_]): ConnectionPoolSettings =
    ConnectionPoolSettings.default(actorSystem.toClassic)
      .withMinConnections(1 + maxInflightRequests / 4)
      .withMaxConnections(maxInflightRequests)
      .withMaxOpenRequests(maxInflightRequests * 4)
      .withResponseEntitySubscriptionTimeout(2 seconds)

  private type QUEUE = SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])]
  private type FLOW_INPUT = (HttpRequest, Promise[HttpResponse])
  private type FLOW_OUTPUT = (Try[HttpResponse], Promise[HttpResponse])
  private type FLOW = Flow[FLOW_INPUT, FLOW_OUTPUT, Http.HostConnectionPool]

  private[this] def queue(poolClientFlow: FLOW, size: Int)(implicit materializer: Materializer) =
    Source.queue[(HttpRequest, Promise[HttpResponse])](size, OverflowStrategy.dropNew)
      .via(poolClientFlow)
      .toMat(Sink.foreach({
        case ((Success(resp), p)) => p.success(resp)
        case ((Failure(e), p))    => p.failure(e)
      }))(Keep.left).run()

  def http(gatewayConfig: GatewayConfig)(implicit actorSystem: ActorSystem[_]) = {
    val queuesPerService: Map[String, QUEUE] = {
      val fromServices = gatewayConfig.services.map {
        service =>

          implicit val classicSystem = actorSystem.toClassic

          val poolClientFlow = Http().cachedHostConnectionPool[Promise[HttpResponse]](
            service.host,
            service.port,
            hostConnectionPoolConfig(service.maxInflightRequests)
          )

          val queueSize = service.maxInflightRequests * 4
          val target = resolveServiceName(service)
          target -> queue(poolClientFlow, queueSize)(Materializer(actorSystem))
      }
      fromServices.toMap
    }

    def resolvePromise(request: HttpRequest, promise: Promise[HttpResponse], queueOfferResult: QueueOfferResult) = {
      queueOfferResult match {
        case QueueOfferResult.Enqueued => promise.future
        case QueueOfferResult.Dropped =>
          Future.failed(new RuntimeException("Queue overflowed. Try again later."))
        case QueueOfferResult.Failure(ex) => Future.failed(ex)
        case QueueOfferResult.QueueClosed => Future.failed(
          new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later.")
        )
      }
    }


    def queueRequest(request: HttpRequest, queue: QUEUE): Future[HttpResponse] = {
      val responsePromise = Promise[HttpResponse]()
      queue.offer(request -> responsePromise).flatMap(resolvePromise(request, responsePromise, _))(actorSystem.executionContext)
    }

    apply(
      gatewayConfig,
      serviceCall => IO.fromFuture {
        IO {
          val target = s"${serviceCall.hitEndpoint.service}:${serviceCall.hitEndpoint.port}"
          val queue = queuesPerService(target)
          queueRequest(serviceCall.request, queue)
        }
      }(IO.contextShift(actorSystem.executionContext))
    )
  }

  private[gateway] def apply
      (gatewayConfig: GatewayConfig, httpRequest: ServiceCall => IO[HttpResponse])
      (implicit actorSystem: ActorSystem[_]) =
    new ServiceExecutionApi(gatewayConfig, httpRequest)
}