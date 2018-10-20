package talos.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.scaladsl.Source
import akka.util.Timeout
import io.circe.generic.auto._
import io.circe.syntax._
import talos.http.HystrixReporter.HystrixDashboardEvent

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

  def main: Source[ServerSentEvent, _] = Source.tick(
    1 second, 2 seconds, HystrixReporter.FetchHystrixEvents
  ).ask[HystrixDashboardEvent](hystrixReporter)
    .map(_.asJson.noSpaces).map(ServerSentEvent(_))


}
