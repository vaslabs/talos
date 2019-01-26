package talos.circuitbreakers.monix


import java.util.concurrent.LinkedBlockingQueue

import cats.effect._
import monix.catnap.CircuitBreaker
import monix.execution.Ack.Continue
import monix.execution.{Ack, Scheduler}
import monix.reactive.observers.Subscriber
import org.scalatest.{BeforeAndAfterAll, Matchers}
import talos.circuitbreakers
import talos.circuitbreakers.monix.MonixCircuitBreaker.EventSubscriber
import talos.events.TalosEvents.model._
import talos.laws.TalosCircuitBreakerLaws

import scala.concurrent.duration._

import scala.concurrent.{ExecutionContext, Future}

class TalosCircuitBreakerEventsSpec extends TalosCircuitBreakerLaws[CircuitBreaker[IO], EventSubscriber, IO]
  with Matchers with BeforeAndAfterAll {

  implicit val _scheduler = Scheduler(ExecutionContext.global)

  val nextMessage = new LinkedBlockingQueue[CircuitBreakerEvent](10000)

  private val eventListener = new Subscriber[CircuitBreakerEvent] {
    override implicit def scheduler: Scheduler = _scheduler

    override def onNext(elem: CircuitBreakerEvent): Future[Ack] = {
      nextMessage.add(elem)
      println(s"Got $elem")
      Future.successful(Continue)
    }

    override def onError(ex: Throwable): Unit = ()

    override def onComplete(): Unit = println("completed")
  }

  override def beforeAll(): Unit = {
    talosCircuitBreaker.eventBus.subscribe(eventListener, classOf[CircuitBreakerEvent])
    ()
  }

  override def afterAll(): Unit = {
    talosCircuitBreaker.eventBus.unsubsribe(eventListener)
  }

  val circuitBreakerName = "testCircuitBreaker"

  implicit val effectClock = Clock.create[IO]

  val circuitBreaker: CircuitBreaker[IO] =
    CircuitBreaker.of[IO](5, resetTimeout).unsafeRunSync()


  implicit val contextShift = IO.contextShift(ExecutionContext.global)

  override def acceptMsg: CircuitBreakerEvent = {
    IO {
        nextMessage.take()
      }.timeout(5 seconds).unsafeRunSync()
  }



  override val talosCircuitBreaker: MonixCircuitBreaker.Instance[IO] =
    MonixCircuitBreaker(
      circuitBreakerName,
      circuitBreaker,
      callTimeout
    )

  override implicit val eventBus: circuitbreakers.EventBus[MonixCircuitBreaker.EventSubscriber] = talosCircuitBreaker.eventBus

}
