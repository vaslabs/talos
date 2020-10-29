package talos.gateway

import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.Http
import kamon.Kamon
import talos.gateway.config.GatewayConfig

import scala.concurrent.Future

class GatewayServer private(gatewayConfig: GatewayConfig)(implicit actorContext: ActorContext[_]) {

  Kamon.init()

  private[gateway] val start: Future[Http.ServerBinding] = {
    val gateway = Gateway(gatewayConfig)(actorContext)
    import scala.concurrent.ExecutionContext.Implicits._

    implicit val actorSystem = actorContext.system

    for {
      serverBinding <- Http().newServerAt(gatewayConfig.interface, gatewayConfig.port).bind(gateway.route)
    } yield serverBinding
  }

}


object GatewayServer {
  def apply()(implicit actorContext: ActorContext[_]): Future[Http.ServerBinding] =
    GatewayConfig.load match {
      case Right(config) => GatewayServer(config)
      case Left(throwable) => Future.failed(new RuntimeException(throwable.toList.mkString("\n")))
    }

  def apply(config: GatewayConfig)(implicit actorSystem: ActorContext[_]): Future[Http.ServerBinding] =
    new GatewayServer(config).start
}