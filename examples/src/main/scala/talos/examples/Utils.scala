package talos.examples

import java.time.ZonedDateTime

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter._
import akka.pattern.CircuitBreaker
import talos.circuitbreakers.akka.AkkaCircuitBreaker
import talos.http.CircuitBreakerEventsSource.CircuitBreakerStats

object Utils {

  import scala.concurrent.duration._

  def statsSample =
    CircuitBreakerStats(
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
      0,
      0,
      0,
      0 nanos,
      Map.empty,
      0 nanos,
      Map.empty,
      1 second
    )

  import scala.concurrent.duration._

  def createCircuitBreaker(name: String = "testCircuitBreaker")
                          (implicit system: ActorSystem[_]): AkkaCircuitBreaker.Instance = {
    AkkaCircuitBreaker(
      name,
      CircuitBreaker(
        system.scheduler.toClassic,
        5,
        2 seconds,
        5 seconds
      )
    )
  }

}