package talos.http

import java.time.Clock

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.{Http, server}
import akka.stream.Materializer
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

class HystrixReporterDirective(implicit actorContext: ActorContext[_], clock: Clock) {

  lazy val hystrixStreamHttpRoute = {
      StatsAggregator.kamon()
      val statsGatherer = actorContext.spawn(CircuitBreakerStatsActor.behaviour, "CircuitBreakerStats")
      val hystrixReporter = new HystrixReporter(statsGatherer)(clock)
      Kamon.addReporter(hystrixReporter)
      val hystrixRouter = new CircuitBreakerEventsSource(statsGatherer) with ServerEventHttpRouter
      hystrixRouter.route
  }
}

class StartServer(
    host: String,
    port: Int
  )(implicit actorContext: ActorContext[_]) {


  val startHttpServer = Kleisli[Future, server.Route, ServerBinding] {
    route =>
      implicit val actorSystem = actorContext.system.toClassic
      implicit val materializer = Materializer(actorContext)
      Http().bindAndHandle(route, host, port)
  }

}