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
import cats.implicits._
import kamon.Kamon
import talos.kamon.StatsAggregator
import talos.kamon.hystrix.HystrixReporter

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait ServerEventHttpRouter extends
      EventStreamMarshalling
      with Directives { source: CircuitBreakerEventsSource =>

  val route = path("hystrix.stream") {
    get {
      complete(source.main)
    }
  }

}


class HystrixReporterServer(
    serverEventTimeout: FiniteDuration,
    host: String,
    port: Int
  )(implicit actorSystem: ActorSystem, timeout: Timeout) {

  import actorSystem.dispatcher
  import akka.actor.typed.scaladsl.adapter._


  private[http] val collectCircuitBreakerStats = Kleisli[Future, Clock, CircuitBreakerEventsSource with ServerEventHttpRouter] {
    clock =>
      actorSystem.toTyped.systemActorOf(StatsAggregator.behavior(), "CircuitBreakerStatsAggregator").map { _ =>
        val statsGatherer = actorSystem.actorOf(CircuitBreakerStatsActor.props, "CircuitBreakerStats")
        val hystrixReporter = new HystrixReporter(statsGatherer)(clock)
        Kamon.addReporter(hystrixReporter)
        new CircuitBreakerEventsSource(serverEventTimeout, statsGatherer) with ServerEventHttpRouter
      }
  }

  private[http] val startHttpServer = Kleisli[Future, CircuitBreakerEventsSource with ServerEventHttpRouter, ServerBinding] {
    source =>
      implicit val actorMaterializer = ActorMaterializer()
      Http().bindAndHandle(source.route, host, port)
  }

  val start = collectCircuitBreakerStats andThen startHttpServer

}