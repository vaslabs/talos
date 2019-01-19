package talos.circuitbreakers

import java.util.concurrent.{Executors, TimeUnit, TimeoutException}

import _root_.akka.actor.{ActorRef, ActorSystem}
import _root_.monix.catnap.CircuitBreaker
import _root_.monix.execution.exceptions.ExecutionRejectedException
import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import talos.events.TalosEvents.model._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


package object monix {

  class AkkaEventBus(implicit actorSystem: ActorSystem) extends EventBus[ActorRef]{

    override def subscribe[T](subscriber: ActorRef, topic: Class[T]): Option[ActorRef] =
      if (actorSystem.eventStream.subscribe(subscriber, topic))
        Some(subscriber)
      else
        None

    override def unsubsribe(a: ActorRef): Unit = actorSystem.eventStream.unsubscribe(a)

    override def publish[A <: AnyRef](a: A): Unit = actorSystem.eventStream.publish(a)
  }

  class MonixCircuitBreaker[F[_]] private (
      val name: String,
      internalCircuitBreaker: CircuitBreaker[F],
      callTimeout: FiniteDuration
  )(implicit eventBus: AkkaEventBus, F: Concurrent[F])
    extends TalosCircuitBreaker[CircuitBreaker[F], F] {

    private[this] val fClock: Clock[F] = Clock.create[F]

    private[this] implicit val timer: Timer[F] = new Timer[F] {
      private[this] val executionContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
      private[this] val ioTimer = IO.timer(executionContext)
      override def clock: Clock[F] = fClock

      override def sleep(duration: FiniteDuration): F[Unit] = F.liftIO(ioTimer.sleep(duration))
    }


    override def protect[A](task: F[A]): F[A] = {
      for {
        startTimer <- fClock.monotonic(TimeUnit.NANOSECONDS)
        protectedTask <- internalCb.protect(task.timeout(callTimeout)).handleErrorWith {
          case t: Throwable =>
            publishError(t, startTimer) *> F.raiseError(t)
        }
        endTimer <- fClock.monotonic(TimeUnit.NANOSECONDS)
        _ = publishSuccess((endTimer - startTimer) nanos)
      } yield (protectedTask)
    }

    override def protectWithFallback[A, E](task: F[A], fallback: F[E]): F[Either[E, A]] =
      protect(task).map[Either[E, A]](Right(_)).handleErrorWith {
        _ =>
          eventBus.publish(FallbackActivated(name))
          fallback.map(Left(_))
      }


    private def publishError(throwable: Throwable, started: Long): F[Unit] = {
      for {
        ended <- fClock.monotonic(TimeUnit.NANOSECONDS)
        elapsedTime = (ended - started) nanos

        errorEvent = throwable match {
          case _: TimeoutException =>
            CallTimeout(name, elapsedTime)
          case _: ExecutionRejectedException =>
            ShortCircuitedCall(name)
          case _ =>
            CallFailure(name, elapsedTime)
        }
      } yield eventBus.publish(errorEvent)

    }


    private def publishSuccess(elapsedTime: FiniteDuration): Unit =
      eventBus.publish(SuccessfulCall(name, elapsedTime))


    private val internalCb = internalCircuitBreaker.doOnClosed(F.delay(eventBus.publish(CircuitClosed(name))))
      .doOnHalfOpen(F.delay(eventBus.publish(CircuitHalfOpen(name))))
      .doOnOpen(F.delay(eventBus.publish(CircuitOpen(name))))

    override val circuitBreaker: F[CircuitBreaker[F]] = F.pure {
      internalCb
    }

  }

  object MonixCircuitBreaker {
    def apply[F[_]](
        name: String,
        circuitBreaker: CircuitBreaker[F],
        callTimeout: FiniteDuration
    )(implicit actorSystem: ActorSystem, async: Concurrent[F]
    ): TalosCircuitBreaker[CircuitBreaker[F], F] = {

      implicit val eventBus = new AkkaEventBus()
      implicit val monixCircuitBreaker = new MonixCircuitBreaker(name, circuitBreaker, callTimeout)
      Talos.circuitBreaker
    }
  }
}
