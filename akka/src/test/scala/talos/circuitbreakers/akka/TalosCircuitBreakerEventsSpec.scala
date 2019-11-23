package talos.circuitbreakers.akka

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorRef
import akka.actor.typed.eventstream.EventStream
import akka.pattern.CircuitBreaker
import cats.effect.IO
import org.scalatest.{BeforeAndAfterAll, Matchers}
import talos.circuitbreakers
import talos.events.TalosEvents.model._
import talos.laws.TalosCircuitBreakerLaws

class TalosCircuitBreakerEventsSpec extends TalosCircuitBreakerLaws[CircuitBreaker, ActorRef[CircuitBreakerEvent], IO]
      with Matchers
      with BeforeAndAfterAll{

  val testKit = ActorTestKit()

  implicit val system = testKit.system

  val circuitBreakerName = "testCircuitBreaker"

  val eventListener = testKit.createTestProbe[CircuitBreakerEvent]("talosEventsListener")
  system.eventStream ! EventStream.Subscribe(eventListener.ref)


  override def afterAll(): Unit = {
    system.eventStream ! EventStream.Unsubscribe(eventListener.ref)
    testKit.shutdownTestKit()
  }

  override val talosCircuitBreaker: AkkaCircuitBreaker.Instance =  AkkaCircuitBreaker(
    circuitBreakerName,
    maxFailures = 5,
    callTimeout = callTimeout,
    resetTimeout = resetTimeout
  )

  override def acceptMsg: CircuitBreakerEvent = eventListener.expectMessageType[CircuitBreakerEvent]

  override implicit val eventBus: circuitbreakers.EventBus[ActorRef[CircuitBreakerEvent]] = new AkkaEventBus()
}
