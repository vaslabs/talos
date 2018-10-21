package talos.kamon

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import kamon.Kamon
import kamon.metric.{Counter, Histogram}
import talos.events.TalosEvents.model._

object StatsAggregator {

  private def kamonCounter(circuitBreakerEvent: CircuitBreakerEvent): Counter = {
    val counter = Kamon.counter(s"circuit-breaker-${circuitBreakerEvent.circuitBreakerName}")
    val refinedTag = "eventType" -> {
      circuitBreakerEvent match {
        case SuccessfulCall(_, _) =>
           "success-call"
        case CallFailure(_, _) =>
          "failed-call"
        case CircuitOpen(_) =>
         "circuit-open"
        case CircuitClosed(_) =>
          "circuit-closed"
        case HalfOpen(_) =>
          "circuit-half-open"
        case CallTimeout(_, _) => "call-timeout"
      }
    }
    counter.refine(refinedTag)
  }

  private def kamonHistogram(circuitBreakerEvent: CircuitBreakerEvent): Histogram = {
    val histogram = Kamon.histogram(s"circuit-breaker-elapsed-time-${circuitBreakerEvent.circuitBreakerName}")
    val refinedTag = "eventType" -> {
      circuitBreakerEvent match {
        case SuccessfulCall(_, elapsedTime) =>
          "success-call"
        case CallFailure(_, elapsedTime) =>
          "failure-call"
        case CallTimeout(_, elapsedTime) =>
          "call-timeout"
      }
    }
    histogram.refine(refinedTag)
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
        case ct @ CallTimeout(identifier, elapsedTime) =>
          kamonCounter(ct)
          kamonHistogram(ct).record(elapsedTime.toNanos)
        case eventsWithoutElapsedTime: CircuitBreakerEvent =>
          kamonCounter(eventsWithoutElapsedTime).increment()
      }
      Behaviors.same
  }
}
