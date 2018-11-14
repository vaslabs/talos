package talos.gateway

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import talos.gateway.config.GatewayConfig

import scala.concurrent.Future

class GatewayServer private(gatewayConfig: GatewayConfig)(implicit actorSystem: ActorSystem) {

  private[gateway] val start: Future[Http.ServerBinding] = {
    val gateway = Gateway(gatewayConfig)
    implicit val actorMaterializer = ActorMaterializer()
    Http().bindAndHandle(gateway.route, gatewayConfig.interface, gatewayConfig.port)
  }

}


object GatewayServer {
  def apply()(implicit actorSystem: ActorSystem): Future[Http.ServerBinding] =
    GatewayConfig.load match {
      case Right(config) => new GatewayServer(config).start
      case Left(throwable) => Future.failed(new RuntimeException(throwable.toList.mkString("\n")))
    }
}