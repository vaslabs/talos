package talos.gateway

import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import talos.gateway.EndpointResolver.HitEndpoint
import talos.gateway.Gateway.ServiceCall
import talos.gateway.config.GatewayConfig

import scala.concurrent.Future

class Gateway private
(gatewayConfig: GatewayConfig, executionApi: ExecutionApi[Future]) {

  private def invalidRoute(error: String): Route = pathEndOrSingleSlash {
    complete(HttpResponse(status = StatusCodes.InternalServerError, entity = error))
  }

  val route: Route = gatewayConfig.services.map(EndpointResolver.resolve).map {
    _.map {
      _ {
        hitEndpoint =>
          extractRequest { httpRequest =>
            complete(executionApi.executeCall(ServiceCall(hitEndpoint, httpRequest)))
          }
      }
    }.left.map(invalidRoute).merge
  }.reduce(_ ~ _)

}

object Gateway {
  def apply(gatewayConfig: GatewayConfig)(implicit actorContext: ActorContext[_]): Gateway =
    apply(gatewayConfig, ExecutionApi.http(gatewayConfig)(actorContext.system))

  private[gateway] def apply(gatewayConfig: GatewayConfig, executionApi: ExecutionApi[Future]): Gateway =
    new Gateway(gatewayConfig, executionApi)

  sealed trait HttpCall

  case class ServiceCall(hitEndpoint: HitEndpoint, request: HttpRequest) extends HttpCall

}
