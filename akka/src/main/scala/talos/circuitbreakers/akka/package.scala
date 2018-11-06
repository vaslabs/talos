package talos.circuitbreakers

import cats.effect.IO
import talos.events.TalosEvents
import talos.events.TalosEvents.model._

import scala.concurrent.duration.FiniteDuration

package object akka {

  import _root_.akka.event.EventStream
  import _root_.akka.pattern.{CircuitBreaker => AkkaCB}
  import _root_.akka.actor.{ActorRef, ActorSystem}

  class AkkaEventBus(implicit actorSystem: ActorSystem) extends EventBus[ActorRef]{

    override def subscribe[T](subscriber: ActorRef, topic: Class[T]): Option[ActorRef] =
      if (actorSystem.eventStream.subscribe(subscriber, topic))
        Some(subscriber)
      else
        None

    override def unsubsribe(a: ActorRef): Unit = actorSystem.eventStream.unsubscribe(a)

    override def publish[A <: AnyRef](a: A): Unit = actorSystem.eventStream.publish(a)
  }

  class AkkaCircuitBreaker[Subscriber] private (val name: String, cbInstance: AkkaCB)
         (implicit eventBus: EventBus[ActorRef]) extends TalosCircuitBreaker[AkkaCB, IO] {

    override def protect[A](task: IO[A]): IO[A] =
      IO.delay {
        protectUnsafe(task)
      }


    private val circuitBreakerInstance = wrap(
      cbInstance,
      name
    )


    private def wrap(circuitBreaker: AkkaCB, identifier: String): AkkaCB = {
      import scala.concurrent.duration._
      def publish(event: EventStream#Event): Unit = eventBus.publish(event)
      circuitBreaker.addOnCallSuccessListener(
        elapsedTime => publish(SuccessfulCall(identifier, elapsedTime nanoseconds))
      ).addOnCallFailureListener(
        elapsedTime => publish(CallFailure(identifier, elapsedTime nanoseconds))
      ).addOnCallTimeoutListener(
        elapsedTime => publish(CallTimeout(identifier, elapsedTime nanoseconds))
      ).addOnOpenListener(
        () => publish(CircuitOpen(identifier))
      ).addOnHalfOpenListener(
        () => publish(CircuitHalfOpen(identifier))
      ).addOnCloseListener(
        () => publish(CircuitClosed(identifier))
      ).addOnCallBreakerOpenListener(
        () => publish(ShortCircuitedCall(identifier))
      )
    }

    override val circuitBreaker: IO[AkkaCB] = IO.pure(circuitBreakerInstance)

    override def protectUnsafe[A](task: IO[A]): A =
      circuitBreakerInstance.callWithSyncCircuitBreaker(() => task.unsafeRunSync())
  }

  object AkkaCircuitBreaker {
    def apply(
       name: String,
       maxFailures: Int,
       callTimeout: FiniteDuration,
       resetTimeout: FiniteDuration
     )(implicit actorSystem: ActorSystem): TalosCircuitBreaker[AkkaCB, IO] = {
      apply(name, AkkaCB(actorSystem.scheduler, maxFailures, callTimeout, resetTimeout))
    }

    def apply(name: String, circuitBreaker: AkkaCB)(implicit actorSystem: ActorSystem): TalosCircuitBreaker[AkkaCB, IO] = {
      implicit val eventBus: EventBus[ActorRef] = new AkkaEventBus()
      implicit val akkaCircuitBreaker: AkkaCircuitBreaker[ActorRef] =
        new AkkaCircuitBreaker[ActorRef](name, circuitBreaker)
      TalosEvents.circuitBreaker
    }
  }

  final implicit class AkkaCircuitBreakerSyntax(val circuitBreaker: AkkaCB) extends AnyVal {
    def withEventReporting(name: String)(implicit actorSystem: ActorSystem): TalosCircuitBreaker[AkkaCB, IO] = {
      AkkaCircuitBreaker(name, circuitBreaker)
    }
  }
}
