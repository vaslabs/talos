package talos.http

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker

object Utils {

  import scala.concurrent.duration._
  def statsSample =
    CircuitBreakerStatsActor.CircuitBreakerStats(
      "myCircuitBreaker",
      0L,
      ZonedDateTime.now(),
      false,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0 nanos,
      Map.empty,
      0 nanos,
      Map.empty,
      1 second
    )

  import talos.events.syntax._
  import scala.concurrent.duration._

  def createCircuitBreaker(name: String = "testCircuitBreaker")
                          (implicit actorSystem: ActorSystem) =
    CircuitBreaker.withEventReporting(
      name,
      actorSystem.scheduler,
      5,
      2 seconds,
      5 seconds
    )

}