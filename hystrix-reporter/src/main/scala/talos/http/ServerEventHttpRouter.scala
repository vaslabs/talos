package talos.http

import java.time.Clock

import akka.actor.{ActorRef, ActorSystem}
import akka.actor.typed.{ActorRef => TypedActorRef}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import akka.util.Timeout
import cats.data.Kleisli
import talos.events.TalosEvents.model.CircuitBreakerEvent
import talos.kamon.StatsAggregator
import talos.kamon.hystrix.HystrixReporter

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import cats.implicits._
import cats._
import kamon.Kamon

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

  import akka.actor.typed.scaladsl.adapter._

  import actorSystem.dispatcher


  private[http] val collectCircuitBreakerStats = Kleisli[Future, Clock, CircuitBreakerEventsSource with ServerEventHttpRouter] {
    clock =>
      actorSystem.toTyped.systemActorOf(StatsAggregator.behavior(), "CircuitBreakerStatsAggregator").map { actor =>
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