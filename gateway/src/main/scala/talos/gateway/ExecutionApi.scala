package talos.gateway

import java.time.Duration
import java.util.concurrent._

import akka.actor.Scheduler
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model.HttpResponse
import akka.pattern.CircuitBreaker
import cats.effect.IO
import cats.implicits._
import talos.circuitbreakers.TalosCircuitBreaker
import talos.gateway.Gateway.HttpCall
import talos.gateway.config.{GatewayConfig, ServiceConfig}

import scala.concurrent.{ExecutionContext, Future}

trait ExecutionApi[F[_]] {
  def executeCall(httpCommand: HttpCall): F[HttpResponse]
}

private class HttpServiceExecutionApi private (gatewayConfig: GatewayConfig) extends ExecutionApi[Future] {

  val executionContexts: Map[String, ExecutionContext] = {
    val fromServices = gatewayConfig.services.map {
      serviceConfig =>
        val executor = new ThreadPoolExecutor(
          Math.toIntExact(serviceConfig.maxInflightRequests / 2),
          serviceConfig.maxInflightRequests,
          1L,
          TimeUnit.MINUTES,
          new SynchronousQueue[Runnable]()
        )
        serviceConfig.host -> ExecutionContext.fromExecutor(executor)
    }
    fromServices.toMap
  }

  val circuitBreakers: Map[String, TalosCircuitBreaker[CircuitBreaker, IO]] = ???

  override def executeCall(httpCommand: HttpCall): Future[HttpResponse] = {
    ???
  }
}

object ExecutionApi {
  def production(gatewayConfig: GatewayConfig): ExecutionApi[Future] = ???
}