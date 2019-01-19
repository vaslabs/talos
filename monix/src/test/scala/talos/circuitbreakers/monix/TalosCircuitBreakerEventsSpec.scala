package talos.circuitbreakers.monix


import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import cats.effect._
import monix.catnap.CircuitBreaker
import org.scalatest.{BeforeAndAfterAll, Matchers}
import talos.circuitbreakers
import talos.events.TalosEvents.model._
import talos.laws.TalosCircuitBreakerLaws

import scala.concurrent.duration._

class TalosCircuitBreakerEventsSpec extends TalosCircuitBreakerLaws[ActorRef, CircuitBreaker[IO], IO]
  with Matchers with BeforeAndAfterAll {

  val testKit = new TestKit(ActorSystem("TalosCircuitBreakerEventsSpec"))
  import testKit._

  private val eventListener = TestProbe("talosMonixEventsListener")
  system.eventStream.subscribe(eventListener.ref, classOf[CircuitBreakerEvent])

  import testKit._

  override def afterAll(): Unit = {
    system.eventStream.unsubscribe(eventListener.ref)
    system.terminate()
    ()
  }

  val circuitBreakerName = "testCircuitBreaker"

  implicit val effectClock = Clock.create[IO]

  val circuitBreaker: CircuitBreaker[IO] =
    CircuitBreaker.of[IO](5, 5 seconds).unsafeRunSync()

  override def acceptMsg: CircuitBreakerEvent = eventListener.expectMsgType[CircuitBreakerEvent]

  override implicit val eventBus: circuitbreakers.EventBus[ActorRef] = new AkkaEventBus()

  implicit val contextShift = IO.contextShift(system.dispatcher)

  override val talosCircuitBreaker: circuitbreakers.TalosCircuitBreaker[CircuitBreaker[IO], IO] =
    MonixCircuitBreaker(
      circuitBreakerName,
      circuitBreaker,
      callTimeout
    )
}
