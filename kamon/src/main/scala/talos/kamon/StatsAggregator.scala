package talos.kamon

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import kamon.Kamon
import kamon.metric.{Counter, Histogram}
import talos.events.TalosEvents.model._

object StatsAggregator {

  private def kamonCounter(circuitBreakerEvent: CircuitBreakerEvent): Counter = {
    val counter = circuitBreakerEvent match {
      case SuccessfulCall(_, elapsedTime) =>
        Kamon.counter("success-call")
      case CallFailure(_, elapsedTime) =>
        Kamon.counter("failed-call")
      case CircuitOpen(_) =>
        Kamon.counter("circuit-open")
      case CircuitClosed(_) =>
        Kamon.counter("circuit-closed")
      case HalfOpen(_) =>
        Kamon.counter("circuit-half-open")
    }
    counter.refine("circuit-breaker" -> circuitBreakerEvent.circuitBreakerName)
  }

  private def kamonHistogram(circuitBreakerEvent: CircuitBreakerEvent): Histogram = {
    val histogram = circuitBreakerEvent match {
      case SuccessfulCall(circuitBreakerName, elapsedTime) =>
        Kamon.histogram("success-call-elapsed-time")
      case CallFailure(circuitBreakerName, elapsedTime) =>
        Kamon.histogram("failure-call-elapsed-time")
    }
    histogram.refine("circuit-breaker" -> circuitBreakerEvent.circuitBreakerName)
  }

  def behavior(): Behavior[CircuitBreakerEvent] = Behaviors.receive {
    (ctx, msg) =>
      msg match {
        case sc @ SuccessfulCall(identifier, elapsedTime) =>
          kamonCounter(sc).increment()
          kamonHistogram(sc).record(elapsedTime.toNanos)
        case cf @ CallFailure(identifier, elapsedTime) =>
          kamonCounter(cf).increment()
          kamonHistogram(cf).record(elapsedTime.toNanos)
        case eventsWithoutElapsedTime: CircuitBreakerEvent =>
          kamonCounter(eventsWithoutElapsedTime).increment()
      }
      Behaviors.same
  }
}
