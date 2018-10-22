package talos.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.scaladsl.Source
import akka.util.Timeout
import talos.http.CircuitBreakerStatsActor.{CircuitBreakerStats, HystrixDashboardEvent}

import scala.concurrent.duration._
class CircuitBreakerEventsSource
      (
        timeout: FiniteDuration,
        bufferSize: Int,
        hystrixReporter: ActorRef
      )
      (
        implicit actorSystem: ActorSystem,
      ) {

  private implicit val streamTimeout: Timeout = Timeout(timeout)

  import json_adapters._
  import io.circe.syntax._

  def main: Source[ServerSentEvent, _] = Source.tick(
    1 second, 2 seconds, CircuitBreakerStatsActor.FetchHystrixEvents
  ).ask[HystrixDashboardEvent](hystrixReporter).mapConcat(
    _.stats.map(_.asJson)
  ).map(_.noSpaces).map(ServerSentEvent(_))


}
