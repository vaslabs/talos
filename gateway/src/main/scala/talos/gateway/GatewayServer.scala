package talos.gateway

import java.time.Clock

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import talos.gateway.config.GatewayConfig
import talos.http.HystrixReporterDirective

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.http.scaladsl.server.Directives._

class GatewayServer private(gatewayConfig: GatewayConfig)(implicit actorSystem: ActorSystem) {

  private[gateway] val start: Future[Http.ServerBinding] = {
    val gateway = Gateway(gatewayConfig)
    implicit val actorMaterializer = ActorMaterializer()
    import scala.concurrent.ExecutionContext.Implicits._

    implicit val timeout: Timeout = Timeout(5 seconds)
    val hystrixDirective =
      new HystrixReporterDirective().hystrixStreamHttpRoute.run(Clock.systemUTC())

    for {
      hystrixStream <- hystrixDirective
      serverBinding <- Http().bindAndHandle(gateway.route ~ hystrixStream, gatewayConfig.interface, gatewayConfig.port)
    } yield serverBinding
  }

}


object GatewayServer {
  def apply()(implicit actorSystem: ActorSystem): Future[Http.ServerBinding] =
    GatewayConfig.load match {
      case Right(config) => new GatewayServer(config).start
      case Left(throwable) => Future.failed(new RuntimeException(throwable.toList.mkString("\n")))
    }
}