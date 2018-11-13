package talos.gateway

import java.util.concurrent._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpResponse
import akka.pattern.CircuitBreaker
import cats.effect.IO
import talos.circuitbreakers.TalosCircuitBreaker
import talos.gateway.Gateway.HttpCall
import talos.gateway.config.GatewayConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import talos.circuitbreakers.akka._

trait ExecutionApi[F[_]] {
  def executeCall(httpCommand: HttpCall): F[HttpResponse]
}

class HttpServiceExecutionApi private[gateway] (gatewayConfig: GatewayConfig)(implicit actorSystem: ActorSystem) extends ExecutionApi[Future] {

  val executionContexts: Map[String, ExecutionContext] = {
    val fromServices = gatewayConfig.services.map {
      serviceConfig =>
        val executor = new ThreadPoolExecutor(
          serviceConfig.maxInflightRequests / 2,
          serviceConfig.maxInflightRequests,
          1L,
          TimeUnit.MINUTES,
          new SynchronousQueue[Runnable]()
        )
        serviceConfig.host -> ExecutionContext.fromExecutor(executor)
    }
    fromServices.toMap
  }

  lazy val circuitBreakers: Map[String, TalosCircuitBreaker[CircuitBreaker, IO]] = {
    gatewayConfig.services.map {
      serviceConfig =>
        val circuitBreaker = CircuitBreaker(
          actorSystem.scheduler,
          5,
          serviceConfig.callTimeout,
          1 minute
        )
        serviceConfig.host -> AkkaCircuitBreaker(serviceConfig.host, circuitBreaker)
    }.toMap
  }

  override def executeCall(httpCommand: HttpCall): Future[HttpResponse] = {
    val executeHttpRequest = httpCommand match {
      case Gateway.ServiceCall(hitEndpoint, request) =>
        for {
          executionContext <- IO.delay(executionContexts(hitEndpoint.service))
          circuitBreaker <- IO.delay(circuitBreakers(hitEndpoint.service))
          _ <- IO.shift(executionContext)
          httpRequest <- EndpointResolver.transformRequest(request, hitEndpoint)
          unprotectedResponse = IO.fromFuture(IO.delay(Http().singleRequest(httpRequest)))
          response <- circuitBreaker.protect(unprotectedResponse)
        } yield response
    }
    executeHttpRequest.unsafeToFuture()
  }
}

object ExecutionApi {
  def production(gatewayConfig: GatewayConfig)(implicit actorSystem: ActorSystem) = new HttpServiceExecutionApi(gatewayConfig)
}