package talos.examples

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import cats.effect.IO
import talos.circuitbreakers.TalosCircuitBreaker
import talos.circuitbreakers.akka.AkkaCircuitBreaker
import talos.http.CircuitBreakerStatsActor

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
                          (implicit actorSystem: ActorSystem): TalosCircuitBreaker[CircuitBreaker, IO] = {
    AkkaCircuitBreaker(
      name,
      CircuitBreaker(
        actorSystem.scheduler,
        5,
        2 seconds,
        5 seconds
      )
    )
  }

}