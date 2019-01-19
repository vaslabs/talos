package talos.kamon

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{Behavior, PostStop, PreRestart, ActorRef => TypedActorRef}
import akka.actor.{ActorSystem, TypedActor}
import akka.util.Timeout
import cats.effect.{CancelToken, IO}
import kamon.Kamon
import kamon.metric.{Counter, Histogram}
import talos.events.TalosEvents.model._

import scala.concurrent.duration._

object StatsAggregator {
  def kamon()(implicit actorSystem: ActorSystem): CancelToken[IO] = IO.fromFuture {
    IO
    {
      actorSystem.toTyped.systemActorOf(behavior(), "TalosStatsAggregator")(Timeout(3 seconds))
    }
  }.unsafeRunCancelable(stop)

  private def stop(cancellationState: Either[Throwable, TypedActorRef[CircuitBreakerEvent]])(implicit actorSystem: ActorSystem) =
    cancellationState match {
      case Right(typedActorRef) =>
        TypedActor(actorSystem).stop(typedActorRef)
        ()
      case Left(throwable) =>
        actorSystem.log.error(throwable, "Can't cancel kamon monitoring for circuit breakers")
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
          println("Giving fallback success")
          FALLBACK_SUCCESS
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

  private[kamon] def behavior(): Behavior[CircuitBreakerEvent] = Behaviors.setup {
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
