package talos.circuitbreakers

import java.util.concurrent._

import _root_.monix.catnap.CircuitBreaker
import _root_.monix.execution.exceptions.ExecutionRejectedException
import _root_.monix.reactive.OverflowStrategy
import _root_.monix.reactive.observers.{BufferedSubscriber, Subscriber}
import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import talos.circuitbreakers.monix.MonixCircuitBreaker.EventSubscriber
import talos.events.TalosEvents.model._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
package object monix {

  class MonixEventBus extends EventBus[EventSubscriber] {

    private[this] final val subscriptions: ConcurrentMap[EventSubscriber, EventSubscriber] = new ConcurrentHashMap

    override def subscribe(subscriber: EventSubscriber): Option[EventSubscriber] = {
      subscriptions.putIfAbsent(subscriber, BufferedSubscriber(subscriber, OverflowStrategy.DropOld(100000)))
      Some(subscriber)
    }

    override def unsubsribe(subscriber: EventSubscriber): Unit = {
      val cancelable = subscriptions.remove(subscriber)
      cancelable.onComplete()
    }

    override def publish[A <: AnyRef](a: A): Unit = a match {
      case cbe: CircuitBreakerEvent =>
        subscriptions.asScala.values.foreach(_.onNext(cbe))
        ()
      case _ =>
    }
  }


  class MonixCircuitBreaker[F[_]] private (
      val name: String,
      internalCircuitBreaker: CircuitBreaker[F],
      callTimeout: FiniteDuration
  )(implicit val eventBus: EventBus[EventSubscriber], F: Concurrent[F])
    extends TalosCircuitBreaker[CircuitBreaker[F], EventSubscriber, F] {

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
      protect(task).map[Either[E, A]](Right(_)) orElse F.suspend[Either[E, A]] {
          fallback.timeout(TalosCircuitBreaker.FAST_FALLBACK_DURATION).map { v =>
            eventBus.publish(FallbackSuccess(name))
            Left(v)
          }
      }.handleErrorWith {
        case _: TimeoutException =>
            eventBus.publish(FallbackRejected(name))
            F.raiseError(new FallbackTimeoutError(name))
        case t =>
          eventBus.publish(FallbackFailure(name))
          F.raiseError(t)
      }


    private def publishError(throwable: Throwable, started: Long): F[Unit] = {
      for {
        ended <- fClock.monotonic(TimeUnit.NANOSECONDS)
        elapsedTime = (ended - started) nanos

        publishAction = throwable match {
          case _: TimeoutException =>
            eventBus.publish(CallTimeout(name, elapsedTime))
          case _: ExecutionRejectedException =>
            ()
          case _ =>
            eventBus.publish(CallFailure(name, elapsedTime))
        }
      } yield publishAction

    }


    private def publishSuccess(elapsedTime: FiniteDuration): Unit =
      eventBus.publish(SuccessfulCall(name, elapsedTime))


    private val internalCb = internalCircuitBreaker
      .doOnClosed(F.delay(eventBus.publish(CircuitClosed(name))))
      .doOnHalfOpen(F.delay(eventBus.publish(CircuitHalfOpen(name))))
      .doOnOpen(F.delay(eventBus.publish(CircuitOpen(name))))
      .doOnRejectedTask(F.delay(eventBus.publish(ShortCircuitedCall(name))))

    override val circuitBreaker: F[CircuitBreaker[F]] = F.pure {
      internalCb
    }

  }

  object MonixCircuitBreaker {
    type Instance[F[_]] = TalosCircuitBreaker[CircuitBreaker[F], EventSubscriber, F]

    type EventSubscriber = Subscriber[CircuitBreakerEvent]

    def apply[F[_]](
        name: String,
        circuitBreaker: CircuitBreaker[F],
        callTimeout: FiniteDuration
    )(implicit async: Concurrent[F]): Instance[F] = {


      implicit val eventBus: EventBus[Subscriber[CircuitBreakerEvent]] = new MonixEventBus()

      implicit val monixCircuitBreaker = new MonixCircuitBreaker(name, circuitBreaker, callTimeout)
      Talos.circuitBreaker
    }
  }
}
