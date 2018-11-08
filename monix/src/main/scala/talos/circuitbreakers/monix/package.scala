package talos.circuitbreakers

import java.util.concurrent.{TimeUnit, TimeoutException}

import _root_.akka.actor.{ActorRef, ActorSystem}
import _root_.monix.eval.{Task, TaskCircuitBreaker}
import talos.events.TalosEvents.model._

import scala.concurrent.duration.{FiniteDuration, _}
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

  import _root_.monix.execution.Scheduler

  class MonixCircuitBreaker private (
      val name: String,
      callTimeout: Duration,
      maxFailures: Int,
      resetTimeout: FiniteDuration,
      scheduler: Scheduler = Scheduler.io()
  )(implicit eventBus: AkkaEventBus)
    extends TalosCircuitBreaker[TaskCircuitBreaker, Task] {

    private implicit val execScheduler = scheduler

    override def protect[A](task: Task[A]): Task[A] = {
      for {
        startTimer <- Task(scheduler.clockRealTime(TimeUnit.NANOSECONDS))
        cb <- circuitBreaker
        protectedTask <- cb.protect[A](task.onErrorHandleWith[A](t => {
          publishError(t, startTimer).flatMap(_ => Task[A](throw t))
        }))
        endTimer <- Task(scheduler.clockRealTime(TimeUnit.NANOSECONDS))
        _ = publishSuccess((endTimer - startTimer) nanos)
      } yield (protectedTask)
    }

    private def publishError(throwable: Throwable, started: Long): Task[Unit] = {
      for {
        ended <- Task(scheduler.clockRealTime(TimeUnit.NANOSECONDS))
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
      onRejected = Task(eventBus.publish(ShortCircuitedCall(name))),
      onOpen = Task(eventBus.publish(CircuitOpen(name)))
    ).memoize

    override def protectUnsafe[A](task: Task[A]): A = protect(task).runSyncUnsafe(callTimeout)

  }

  object MonixCircuitBreaker {
    def apply(
        name: String,
        callTimeout: Duration,
        maxFailures: Int,
        resetTimeout: FiniteDuration)(implicit actorSystem: ActorSystem): TalosCircuitBreaker[TaskCircuitBreaker, Task] = {

      implicit val eventBus = new AkkaEventBus()
      implicit val monixCircuitBreaker = new MonixCircuitBreaker(name, callTimeout, maxFailures, resetTimeout)
      Talos.circuitBreaker
    }
  }
}
