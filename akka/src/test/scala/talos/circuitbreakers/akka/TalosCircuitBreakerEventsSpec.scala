package talos.circuitbreakers.akka

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.CircuitBreaker
import akka.testkit.{TestKit, TestProbe}
import cats.effect.IO
import org.scalatest.{BeforeAndAfterAll, Matchers}
import talos.circuitbreakers
import talos.events.TalosEvents.model._
import talos.laws.TalosCircuitBreakerLaws

class TalosCircuitBreakerEventsSpec extends TalosCircuitBreakerLaws[CircuitBreaker, ActorRef, IO]
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

  override val talosCircuitBreaker: AkkaCircuitBreaker.Instance =  AkkaCircuitBreaker(
    circuitBreakerName,
    maxFailures = 5,
    callTimeout = callTimeout,
    resetTimeout = resetTimeout
  )

  override def acceptMsg: CircuitBreakerEvent = eventListener.expectMsgType[CircuitBreakerEvent]

  override implicit val eventBus: circuitbreakers.EventBus[ActorRef] = new AkkaEventBus()
}
