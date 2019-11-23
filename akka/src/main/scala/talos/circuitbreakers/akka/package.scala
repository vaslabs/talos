package talos.circuitbreakers

import java.util.concurrent.Executors

import _root_.akka.pattern.{CircuitBreaker => AkkaCB}
import _root_.akka.actor.typed.ActorSystem
import _root_.akka.actor.typed.scaladsl.adapter._
import _root_.akka.actor.typed.eventstream.EventStream
import _root_.akka.actor.typed.ActorRef
import cats.effect.IO
import talos.circuitbreakers.akka.AkkaCircuitBreaker.Instance
import talos.events.TalosEvents.model._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, TimeoutException}
import scala.reflect.ClassTag

package object akka {


  class AkkaEventBus[T](implicit system: ActorSystem[_], classTag: ClassTag[T]) extends EventBus[ActorRef[T]]{

    override def subscribe(subscriber: ActorRef[T]): Option[ActorRef[T]] = {
      system.eventStream ! EventStream.Subscribe(subscriber)
      Some(subscriber)
    }

    override def unsubsribe(a: ActorRef[T]): Unit = system.eventStream ! EventStream.Unsubscribe(a)

    override def publish[MSG](msg: MSG): Unit = system.eventStream ! EventStream.Publish(msg)
  }


  class AkkaCircuitBreaker[T] private (val name: String, cbInstance: AkkaCB)
         (implicit val eventBus: EventBus[ActorRef[T]]) extends TalosCircuitBreaker[AkkaCB, ActorRef[T], IO] {


    private[AkkaCircuitBreaker] implicit val timer = IO.timer(AkkaCircuitBreaker.fallbackTimeoutExecutionContext)
    private[AkkaCircuitBreaker] implicit val contextShift = IO.contextShift(AkkaCircuitBreaker.forkIOExecutionContext)

    override def protect[A](task: IO[A]): IO[A] =
      IO.fromFuture {
        IO(protectUnsafe(task))
      }(contextShift)

    override def protectWithFallback[A, E](task: IO[A], fallback: IO[E]): IO[Either[E, A]] = {
      protect(task).map[Either[E, A]](Right(_)).handleErrorWith {
        _ =>
          fallback.timeout(TalosCircuitBreaker.FAST_FALLBACK_DURATION).map { v =>
            eventBus.publish(FallbackSuccess(name))
            Left(v)
          }.handleErrorWith {
            case _: TimeoutException =>
              eventBus.publish(FallbackRejected(name))
              IO.raiseError(new FallbackTimeoutError(name))
            case t =>
              eventBus.publish(FallbackFailure(name))
              IO.raiseError(t)
          }
      }
    }


    private val circuitBreakerInstance = wrap(
      cbInstance,
      name
    )


    private def wrap(circuitBreaker: AkkaCB, identifier: String): AkkaCB = {
      def publish[A <: AnyRef](event: A): Unit = eventBus.publish(event)
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

    private def protectUnsafe[A](task: IO[A]): Future[A] =
      circuitBreakerInstance.callWithCircuitBreaker(() => task.unsafeToFuture())

  }

  object AkkaCircuitBreaker {
    type Instance[T] = TalosCircuitBreaker[AkkaCB, ActorRef[T], IO]

    def apply[T](
       name: String,
       maxFailures: Int,
       callTimeout: FiniteDuration,
       resetTimeout: FiniteDuration
     )(implicit actorSystem: ActorSystem[_], classTag: ClassTag[T]): Instance[T] = {
      apply(name, AkkaCB(actorSystem.scheduler.toClassic, maxFailures, callTimeout, resetTimeout))
    }

    def apply[T](name: String, circuitBreaker: AkkaCB)(implicit actorSystem: ActorSystem[_], classTag: ClassTag[T]): Instance[T] = {
      implicit val eventBus: EventBus[ActorRef[T]] = new AkkaEventBus[T]()
      implicit val akkaCircuitBreaker: AkkaCircuitBreaker[T] =
        new AkkaCircuitBreaker(name, circuitBreaker)
      Talos.circuitBreaker
    }

    private[akka] val fallbackTimeoutExecutionContext = ExecutionContext.fromExecutor(Executors.newScheduledThreadPool(2))
    private[akka] val forkIOExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))

  }

  final implicit class AkkaCircuitBreakerSyntax(val circuitBreaker: AkkaCB) extends AnyVal {
    def withEventReporting[T](name: String)(implicit actorSystem: ActorSystem[_], classTag: ClassTag[T]): Instance[T] = {
      AkkaCircuitBreaker(name, circuitBreaker)
    }
  }

}
