package talos.http

import java.time.Clock

import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.{Http, server}
import akka.stream.ActorMaterializer
import cats.data.Kleisli
import kamon.Kamon
import talos.kamon.StatsAggregator
import talos.kamon.hystrix.HystrixReporter

import scala.concurrent.Future

trait ServerEventHttpRouter extends
      EventStreamMarshalling
      with Directives { source: CircuitBreakerEventsSource =>

  val route = path("hystrix.stream") {
    get {
      complete(source.main)
    }
  }

}

class HystrixReporterDirective(implicit actorSystem: ActorSystem, clock: Clock) {

  lazy val hystrixStreamHttpRoute = {
      StatsAggregator.kamon()
      val statsGatherer = actorSystem.actorOf(CircuitBreakerStatsActor.props, "CircuitBreakerStats")
      val hystrixReporter = new HystrixReporter(statsGatherer)(clock)
      Kamon.addReporter(hystrixReporter)
      val hystrixRouter = new CircuitBreakerEventsSource(statsGatherer) with ServerEventHttpRouter
      hystrixRouter.route
  }
}

class StartServer(
    host: String,
    port: Int
  )(implicit actorSystem: ActorSystem) {


  val startHttpServer = Kleisli[Future, server.Route, ServerBinding] {
    route =>
      implicit val actorMaterializer = ActorMaterializer()
      Http().bindAndHandle(route, host, port)
  }

}