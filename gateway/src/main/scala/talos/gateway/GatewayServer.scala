package talos.gateway

import java.time.Clock

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import talos.gateway.config.GatewayConfig
import talos.http.HystrixReporterDirective

import scala.concurrent.Future

class GatewayServer private(gatewayConfig: GatewayConfig)(implicit actorContext: ActorContext[_]) {

  private[gateway] val start: Future[Http.ServerBinding] = {
    val gateway = Gateway(gatewayConfig)(actorContext)
    implicit val materializer = Materializer(actorContext)
    import scala.concurrent.ExecutionContext.Implicits._

    implicit val testClock = Clock.systemUTC()
    implicit val actorSystem = actorContext.system.toClassic
    val hystrixDirective =
      new HystrixReporterDirective().hystrixStreamHttpRoute

    for {
      serverBinding <- Http().bindAndHandle(gateway.route ~ hystrixDirective, gatewayConfig.interface, gatewayConfig.port)
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