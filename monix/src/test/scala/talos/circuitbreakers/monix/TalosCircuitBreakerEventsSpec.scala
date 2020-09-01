package talos.circuitbreakers.monix


import java.util.concurrent.{ArrayBlockingQueue, TimeUnit, TimeoutException}

import cats.effect._
import monix.catnap.CircuitBreaker
import monix.execution.Ack.Continue
import monix.execution.{Ack, Scheduler}
import monix.reactive.observers.Subscriber
import org.scalatest.BeforeAndAfterAll
import talos.circuitbreakers
import talos.circuitbreakers.monix.MonixCircuitBreaker.EventSubscriber
import talos.events.TalosEvents.model._
import talos.laws.TalosCircuitBreakerLaws

import scala.concurrent.{ExecutionContext, Future}
import org.scalatest.matchers.should.Matchers

class TalosCircuitBreakerEventsSpec extends TalosCircuitBreakerLaws[CircuitBreaker[IO], EventSubscriber, IO]
  with Matchers with BeforeAndAfterAll {

  implicit val _scheduler = Scheduler(ExecutionContext.global)

  val nextMessage = new ArrayBlockingQueue[CircuitBreakerEvent](100000)

  private val eventListener = new Subscriber[CircuitBreakerEvent] {
    override implicit def scheduler: Scheduler = _scheduler

    override def onNext(elem: CircuitBreakerEvent): Future[Ack] = {
      nextMessage.add(elem)
      Future.successful(Continue)
    }

    override def onError(ex: Throwable): Unit = ()

    override def onComplete(): Unit = println("completed")
  }

  override def beforeAll(): Unit =
    talosCircuitBreaker.eventBus.subscribe(eventListener).fold(())(_ => ())


  override def afterAll(): Unit = talosCircuitBreaker.eventBus.unsubsribe(eventListener)


  val circuitBreakerName = "testCircuitBreaker"

  implicit val effectClock = Clock.create[IO]

  val circuitBreaker: CircuitBreaker[IO] =
    CircuitBreaker.of[IO](5, resetTimeout).unsafeRunSync()


  implicit val contextShift = IO.contextShift(ExecutionContext.global)


  override def acceptMsg: CircuitBreakerEvent = {
    val res = nextMessage.poll(5, TimeUnit.SECONDS)
    if (res == null)
      throw new TimeoutException()
    else
      res
  }



  override val talosCircuitBreaker: MonixCircuitBreaker.Instance[IO] =
    MonixCircuitBreaker(
      circuitBreakerName,
      circuitBreaker,
      callTimeout
    )

  override implicit val eventBus: circuitbreakers.EventBus[MonixCircuitBreaker.EventSubscriber] = talosCircuitBreaker.eventBus

}
