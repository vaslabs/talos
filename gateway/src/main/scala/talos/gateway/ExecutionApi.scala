package talos.gateway

import akka.actor.ActorSystem
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpResponse
import akka.pattern.CircuitBreaker
import cats.effect.IO
import com.typesafe.config.ConfigFactory
import talos.circuitbreakers.TalosCircuitBreaker
import talos.circuitbreakers.akka._
import talos.gateway.Gateway.{HttpCall, ServiceCall}
import talos.gateway.config.GatewayConfig

import scala.concurrent.Future
import scala.concurrent.duration._

trait ExecutionApi[F[_]] {
  def executeCall(httpCommand: HttpCall): F[HttpResponse]
}

class ServiceExecutionApi private[gateway](gatewayConfig: GatewayConfig, httpExecution: ServiceCall => IO[HttpResponse])(implicit actorSystem: ActorSystem) extends ExecutionApi[Future] {



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
  def http(gatewayConfig: GatewayConfig)(implicit actorSystem: ActorSystem) = {
    val executionContexts: Map[String, ConnectionPoolSettings] = {
      val fromServices = gatewayConfig.services.map {
        service =>
           val bulkeadingConfigString = s"""
            akka.http.host-connection-pool {
            min-connections = ${service.maxInflightRequests / 4},
            max-connections = ${service.maxInflightRequests},
            max-open-requests = ${service.maxInflightRequests * 2},
            }
          """
          val bulkeadingConfig =
            actorSystem.settings.config.resolveWith(ConfigFactory.parseString(bulkeadingConfigString))
          service.host -> ConnectionPoolSettings(bulkeadingConfig)
      }
      fromServices.toMap
    }
    apply(
      gatewayConfig,
      request => IO.fromFuture {
        IO {
          Http().singleRequest(request.request, settings = executionContexts(request.hitEndpoint.service))
        }
      }
    )
  }

  private[gateway] def apply
      (gatewayConfig: GatewayConfig, httpRequest: ServiceCall => IO[HttpResponse])
      (implicit actorSystem: ActorSystem) =
    new ServiceExecutionApi(gatewayConfig, httpRequest)
}