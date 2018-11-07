package talos.kamon

import akka.actor.typed.{Behavior, PostStop, PreRestart}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._

import kamon.Kamon
import kamon.metric.{Counter, Histogram}

import talos.events.TalosEvents.model._

object StatsAggregator {

  object Keys {
    val CounterPrefix = "circuit-breaker-"
    val HistrogramPrefix = "circuit-breaker-elapsed-time-"

    val Success = "success-call"
    val Failure = "failed-call"
    val Open = "circuit-open"
    val Closed = "circuit-closed"
    val HalfOpen = "circuit-half-open"
    val Timeout = "call-timeout"
    val ShortCircuit = "short-circuited"

    def extractName(circuitBreakerEvent: CircuitBreakerEvent): String =
      circuitBreakerEvent match {
        case SuccessfulCall(_, _) =>
          Success
        case ShortCircuitedCall(_) =>
          ShortCircuit
        case CallFailure(_, _) =>
          Failure
        case CircuitOpen(_) =>
          Open
        case CircuitClosed(_) =>
          Closed
        case CircuitHalfOpen(_) =>
          HalfOpen
        case CallTimeout(_, _) => Timeout
      }
  }

  private def kamonCounter(circuitBreakerEvent: CircuitBreakerEvent): Counter = {
    val counter = Kamon.counter(s"${Keys.CounterPrefix}${circuitBreakerEvent.circuitBreakerName}")
    val refinedTag = "eventType" -> Keys.extractName(circuitBreakerEvent)
    counter.refine(refinedTag)
  }

  private def kamonHistogram(circuitBreakerEvent: CircuitBreakerEvent): Histogram = {
    val histogram = Kamon.histogram(s"${Keys.HistrogramPrefix}${circuitBreakerEvent.circuitBreakerName}")
    val refinedTag = "eventType" -> Keys.extractName(circuitBreakerEvent)
    histogram.refine(refinedTag)
  }

  def behavior(): Behavior[CircuitBreakerEvent] = Behaviors.setup {
    ctx => {
      ctx.system.toUntyped.eventStream.subscribe(ctx.self.toUntyped, classOf[CircuitBreakerEvent])
      Behaviors.receive[CircuitBreakerEvent] {
        (_, msg) =>
          msg match {
            case sc@SuccessfulCall(_, elapsedTime) =>
              kamonCounter(sc).increment()
              kamonHistogram(sc).record(elapsedTime.toNanos)
            case cf@CallFailure(_, elapsedTime) =>
              kamonCounter(cf).increment()
              kamonHistogram(cf).record(elapsedTime.toNanos)
            case ct@CallTimeout(_, elapsedTime) =>
              kamonCounter(ct)
              kamonHistogram(ct).record(elapsedTime.toNanos)
            case eventsWithoutElapsedTime: CircuitBreakerEvent =>
              kamonCounter(eventsWithoutElapsedTime).increment()
          }
          Behaviors.same
      }
    }.receiveSignal {
      case (ctx, PostStop) =>
        ctx.system.toUntyped.eventStream.unsubscribe(ctx.self.toUntyped)
        Behaviors.same
      case (ctx, PreRestart) =>
        ctx.system.toUntyped.eventStream.subscribe(ctx.self.toUntyped, classOf[CircuitBreakerEvent])
        Behaviors.same
    }

  }
}
