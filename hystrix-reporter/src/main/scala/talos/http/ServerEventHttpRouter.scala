package talos.http

import java.time.Clock

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import akka.util.Timeout
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

class HystrixReporterDirective(implicit actorSystem: ActorSystem, timeout: Timeout) {
  import akka.actor.typed.scaladsl.adapter._
  import actorSystem.dispatcher

  val collectCircuitBreakerStats = Kleisli[Future, Clock, CircuitBreakerEventsSource with ServerEventHttpRouter] {
    clock =>
      actorSystem.toTyped.systemActorOf(StatsAggregator.behavior(), "CircuitBreakerStatsAggregator").map { _ =>
        val statsGatherer = actorSystem.actorOf(CircuitBreakerStatsActor.props, "CircuitBreakerStats")
        val hystrixReporter = new HystrixReporter(statsGatherer)(clock)
        Kamon.addReporter(hystrixReporter)
        new CircuitBreakerEventsSource(statsGatherer) with ServerEventHttpRouter
      }
  }
}

class HystrixReporterServer(
    host: String,
    port: Int
  )(implicit actorSystem: ActorSystem) {


  val startHttpServer = Kleisli[Future, CircuitBreakerEventsSource with ServerEventHttpRouter, ServerBinding] {
    source =>
      implicit val actorMaterializer = ActorMaterializer()
      Http().bindAndHandle(source.route, host, port)
  }

}