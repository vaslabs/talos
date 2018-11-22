package talos.gateway

import java.time.Clock

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import talos.gateway.config.GatewayConfig
import talos.http.HystrixReporterDirective

import scala.concurrent.Future

class GatewayServer private(gatewayConfig: GatewayConfig)(implicit actorSystem: ActorSystem) {

  private[gateway] val start: Future[Http.ServerBinding] = {
    val gateway = Gateway(gatewayConfig)
    implicit val actorMaterializer = ActorMaterializer()
    import scala.concurrent.ExecutionContext.Implicits._

    implicit val TestClock = Clock.systemUTC()
    val hystrixDirective =
      new HystrixReporterDirective().hystrixStreamHttpRoute

    for {
      serverBinding <- Http().bindAndHandle(gateway.route ~ hystrixDirective, gatewayConfig.interface, gatewayConfig.port)
    } yield serverBinding
  }

}


object GatewayServer {
  def apply()(implicit actorSystem: ActorSystem): Future[Http.ServerBinding] =
    GatewayConfig.load match {
      case Right(config) => GatewayServer(config)
      case Left(throwable) => Future.failed(new RuntimeException(throwable.toList.mkString("\n")))
    }

  def apply(config: GatewayConfig)(implicit actorSystem: ActorSystem): Future[Http.ServerBinding] =
    new GatewayServer(config).start
}