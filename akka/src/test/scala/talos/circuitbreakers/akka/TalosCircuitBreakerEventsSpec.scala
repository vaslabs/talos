package talos.circuitbreakers.akka

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.CircuitBreaker
import akka.testkit.{TestKit, TestProbe}
import cats.effect.IO
import org.scalatest.{BeforeAndAfterAll, Matchers}
import talos.circuitbreakers
import talos.circuitbreakers.TalosCircuitBreaker
import talos.events.TalosEvents.model._
import talos.laws.TalosCircuitBreakerLaws

import scala.concurrent.duration._
class TalosCircuitBreakerEventsSpec extends TalosCircuitBreakerLaws[ActorRef, CircuitBreaker, IO]
      with Matchers
      with BeforeAndAfterAll{

  val testKit = new TestKit(ActorSystem("TalosCircuitBreakerEventsSpec"))

  import testKit._

  val circuitBreakerName = "testCircuitBreaker"

  val eventListener = TestProbe("talosEventsListener")
  system.eventStream.subscribe(eventListener.ref, classOf[CircuitBreakerEvent])


  override def afterAll(): Unit = {
    system.eventStream.unsubscribe(eventListener.ref)
    system.terminate()
    ()
  }

  override val talosCircuitBreaker: TalosCircuitBreaker[CircuitBreaker, IO] =  AkkaCircuitBreaker(
    circuitBreakerName,
    maxFailures = 5,
    callTimeout = callTimeout,
    resetTimeout = 5 seconds
  )

  override def acceptMsg: CircuitBreakerEvent = eventListener.expectMsgType[CircuitBreakerEvent]

  override implicit val eventBus: circuitbreakers.EventBus[ActorRef] = new AkkaEventBus()
}
