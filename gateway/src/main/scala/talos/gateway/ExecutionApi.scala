package talos.gateway

import akka.http.scaladsl.model.HttpResponse
import talos.gateway.Gateway.HttpCall
import talos.gateway.config.GatewayConfig

import scala.concurrent.Future

trait ExecutionApi[F[_]] {
  def executeCall(httpCommand: HttpCall): F[HttpResponse]
}

object ExecutionApi {
  def production(gatewayConfig: GatewayConfig): ExecutionApi[Future] = ???
}