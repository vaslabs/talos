package talos.kamon

import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, PreRestart}
import cats.effect.{CancelToken, IO}
import kamon.Kamon
import kamon.metric.{Counter, Histogram}
import talos.events.TalosEvents.model._

object StatsAggregator {
  def kamon()(implicit actorContext: ActorContext[_]): CancelToken[IO] = IO.suspend {
      actorContext.spawn(behavior(), "TalosStatsAggregator")
      IO.never
  }.runCancelable(stop).unsafeRunSync()

  private def stop(cancellationState: Either[Throwable, ActorRef[CircuitBreakerEvent]])(implicit actorContext: ActorContext[_]) =
    IO {
      cancellationState match {
        case Right(eventListener) =>
          actorContext.stop(eventListener)
        case Left(throwable) =>
          actorContext.log.error("Can't cancel kamon monitoring for circuit breakers", throwable)
      }
    }


  object Keys {
    val CounterPrefix = "circuit-breaker-"
    val HistrogramPrefix = "circuit-breaker-elapsed-time-"

    val SUCCESS = "success-call"
    val FAILURE = "failed-call"
    val CIRCUIT_OPEN = "circuit-open"
    val CIRCUIT_CLOSED = "circuit-closed"
    val HALF_OPEN = "circuit-half-open"
    val TIMEOUT = "call-timeout"
    val SHORT_CIRCUITED = "short-circuited"
    val FALLBACK_SUCCESS = "fallback-success"
    val FALLBACK_FAILURE = "fallback-failure"
    val FALLBACK_REJECTED = "fallback-rejected"

    def extractName(circuitBreakerEvent: CircuitBreakerEvent): String =
      circuitBreakerEvent match {
        case SuccessfulCall(_, _) =>
          SUCCESS
        case ShortCircuitedCall(_) =>
          SHORT_CIRCUITED
        case CallFailure(_, _) =>
          FAILURE
        case CircuitOpen(_) =>
          CIRCUIT_OPEN
        case CircuitClosed(_) =>
          CIRCUIT_CLOSED
        case CircuitHalfOpen(_) =>
          HALF_OPEN
        case CallTimeout(_, _) =>
          TIMEOUT
        case FallbackSuccess(_) =>
          FALLBACK_SUCCESS
        case FallbackFailure(_) =>
          FALLBACK_FAILURE
        case FallbackRejected(_) =>
          FALLBACK_REJECTED
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

  private[kamon] def behavior(): Behavior[CircuitBreakerEvent] = Behaviors.setup[CircuitBreakerEvent] {
    ctx => {
      ctx.system.eventStream ! EventStream.Subscribe[CircuitBreakerEvent](ctx.self)
      postSubscribeBehaviour()
    }
  }

  private def postSubscribeBehaviour(): Behavior[CircuitBreakerEvent] =
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
    }.receiveSignal {
      case (ctx, PostStop) =>
      ctx.system.eventStream ! EventStream.Unsubscribe(ctx.self)
      Behaviors.same
      case (ctx, PreRestart) =>
      ctx.system.eventStream ! EventStream.Subscribe(ctx.self)
      Behaviors.same
    }
}
