package talos.events

import akka.actor.{ActorSystem, Scheduler}
import akka.pattern.CircuitBreaker

import scala.concurrent.duration.FiniteDuration

object syntax {

  implicit final class CircuitBreakerFactory(val circuitBreaker: CircuitBreaker.type) extends AnyVal {
    def withEventReporting(
      identifier: String,
      scheduler: Scheduler,
      maxFailures: Int,
      callTimeout: FiniteDuration,
      resetTimeout: FiniteDuration
    )(implicit actorSystem: ActorSystem): CircuitBreaker =
      TalosEvents.wrap(circuitBreaker.apply(scheduler, maxFailures, callTimeout, resetTimeout), identifier)
  }
}


