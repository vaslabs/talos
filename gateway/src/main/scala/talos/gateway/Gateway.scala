package talos.gateway

import akka.http.scaladsl.model.{HttpMethod, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import talos.gateway.EndpointResolver.HitEndpoint
import talos.gateway.Gateway.ServiceCall
import talos.gateway.config.GatewayConfig

import scala.concurrent.Future
import scala.util._


class Gateway private
(gatewayConfig: GatewayConfig, executionApi: ExecutionApi[Future]) {

  private def invalidRoute(error: String): Route = pathEndOrSingleSlash {
    complete(HttpResponse(status = StatusCodes.InternalServerError, entity = error))
  }


  val route: Route = gatewayConfig.services.map(EndpointResolver.resolve).map {
    _.map {
      _ {
        hitEndpoint =>
          extractMethod { httpMethod =>
            onComplete(executionApi.executeCall(ServiceCall(httpMethod, hitEndpoint))) {
              case Success(value) => complete(value)
              case Failure(_) => complete(StatusCodes.InternalServerError)
            }
          }
      }
    }.left.map(invalidRoute).merge
  }.reduce(_ ~ _)

}

object Gateway {
  def apply(gatewayConfig: GatewayConfig): Gateway =
    apply(gatewayConfig, ExecutionApi.production(gatewayConfig))

  private[gateway] def apply(gatewayConfig: GatewayConfig, executionApi: ExecutionApi[Future]): Gateway =
    new Gateway(gatewayConfig, executionApi)

  sealed trait HttpCall

  case class ServiceCall(httpMethod: HttpMethod, hitEndpoint: HitEndpoint) extends HttpCall

}
