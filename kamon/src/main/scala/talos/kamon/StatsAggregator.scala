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
  }.unsafeRunCancelable(stopStatsAggregator)

  private def stopStatsAggregator(cancellationState: Either[Throwable, TypedActorRef[CircuitBreakerEvent]])(implicit actorSystem: ActorSystem) =
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
