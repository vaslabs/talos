package talos.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.scaladsl.Source
import akka.util.Timeout
import talos.http.CircuitBreakerStatsActor.{HystrixDashboardEvent}

import scala.concurrent.duration._
class CircuitBreakerEventsSource
      (timeout: FiniteDuration, hystrixReporter: ActorRef)
      (implicit actorSystem: ActorSystem) {

  private implicit val streamTimeout: Timeout = Timeout(timeout)

  import json_adapters._
  import io.circe.syntax._

  final def main: Source[ServerSentEvent, _] = Source.tick(
    1 second, 2 seconds, CircuitBreakerStatsActor.FetchHystrixEvents
  ).ask[HystrixDashboardEvent](hystrixReporter).mapConcat(
    _.stats.map(_.asJson)
  ).map(_.noSpaces).map(ServerSentEvent(_))
    .keepAlive(1.second, () => ServerSentEvent.heartbeat)


}
