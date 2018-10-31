package talos.events

import java.util.concurrent.{TimeUnit, TimeoutException}

import akka.event.EventStream
import akka.pattern
import cats.Monad
import cats.effect.IO
import monix.eval.{Task, TaskCircuitBreaker}
import talos.events.TalosEvents.model._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object CircuitBreakers {


  trait CircuitBreaker[C] {

    def name: String

    def protect[A, F[_]](task: F[A]): F[A]

    def circuitBreaker[C, F[C]](implicit F: Monad[F]): F[C]

    def eventBus[S](implicit eventBus: EventBus[S]): EventBus[S] = eventBus
  }

  trait EventBus[S] {
    def subscribe[T](subscriber: S, topic: Class[T]): Option[S]

    def unsubsribe(a: S): Unit

    def publish[A](a: A): Unit
  }

}

object Monix {

  import CircuitBreakers._

  class MonixCircuitBreaker(val name: String, maxFailures: Int, resetTimeout: FiniteDuration)
      (implicit executionContext: ExecutionContext, eventBus: Akka.AkkaEventBus)
        extends CircuitBreakers.CircuitBreaker[TaskCircuitBreaker] {

    private val timer: Task[Long] = Task.fromIO(IO.timer(executionContext).clockRealTime(TimeUnit.NANOSECONDS))

    override def protect[A, F[_]](task: F[A])(implicit F: Task[F]): F[A] = {
      for {
        startTimer <- timer
        cb <- circuitBreaker
        protectedTask <- cb.protect(task.onErrorHandle(t => {
          publishError(t, startTimer)
          throw t
        }))
        endTimer <- timer
        _ = publishSuccess((endTimer - startTimer) nanos)
      } yield (protectedTask)
    }

    private def publishError(throwable: Throwable, started: Long): Task[Unit] = {
      for {
        ended <- timer

        elapsedTime = (ended - started) nanos

        errorEvent = throwable match {
          case _: TimeoutException =>
            CallTimeout(name, elapsedTime)
          case _ =>
            CallFailure(name, elapsedTime)
        }
      } yield eventBus.publish(errorEvent)

    }


    private def publishSuccess(elapsedTime: FiniteDuration): Unit = eventBus.publish(SuccessfulCall(name, elapsedTime))


    override val circuitBreaker: Task[TaskCircuitBreaker] = TaskCircuitBreaker(
      maxFailures,
      resetTimeout,
      onClosed = Task(eventBus.publish(CircuitClosed(name))),
      onHalfOpen = Task(eventBus.publish(CircuitHalfOpen(name))),
      onRejected = Task(eventBus.publish(ShortCircuitedCall(name)))
    ).memoize

  }

}

object Akka {
  import CircuitBreakers._
  import akka.actor.{ActorRef, ActorSystem}
  import cats.implicits._

  class AkkaEventBus(implicit actorSystem: ActorSystem) extends EventBus[ActorRef]{

    val eventStream = actorSystem.eventStream
    def publish(event: EventStream#Event) = eventStream.publish(event)


    override def subscribe[T](subscriber: ActorRef, topic: Class[T]): Option[ActorRef] =
      if (eventStream.subscribe(subscriber, topic))
          Some(subscriber)
      else
          None

    override def unsubsribe(a: ActorRef): Unit = eventStream.unsubscribe(a)

    override def publish[A](a: A): Unit = publish(a)
  }

  class AkkaCircuitBreaker[M[_]](
          val name: String,
          maxFailures: Int,
          callTimeout: FiniteDuration,
          resetTimeout: FiniteDuration)
          (implicit actorSystem: ActorSystem, M: Monad[M]) extends CircuitBreaker[pattern.CircuitBreaker, M] {

    override def protect[A](task: M[A]): M[A] =
      circuitBreaker

    private val circuitBreakerInstance = TalosEvents.wrap(
      pattern.CircuitBreaker(
        actorSystem.scheduler,
        maxFailures,
        callTimeout,
        resetTimeout,
      ),
      name
    )

    override val circuitBreaker: M[pattern.CircuitBreaker] = M.pure(circuitBreakerInstance)
  }
}
