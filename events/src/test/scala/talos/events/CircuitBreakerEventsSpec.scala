package talos.events

import akka.actor.ActorSystem
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import akka.pattern.CircuitBreaker
import akka.testkit.{TestKit, TestProbe}
import talos.events.TalosEvents.model._

import scala.concurrent.duration._
import scala.util.Try
class CircuitBreakerEventsSpec extends
      TestKit(ActorSystem("CircuitBreakerEvents"))
      with WordSpecLike
      with Matchers
      with BeforeAndAfterAll{

  override def afterAll(): Unit = system.terminate()

  "a circuit breaker" can {
    val circuitBreakerName = "testCircuitBreaker"
    import syntax._
    import scala.concurrent.ExecutionContext.Implicits.global
    val circuitBreakerWithEventStreamReporting =
      CircuitBreaker.withEventReporting(
        circuitBreakerName,
        system.scheduler,
        maxFailures = 5,
        callTimeout = 1 second,
        resetTimeout = 5 seconds
    )
    val eventListener = TestProbe("talosEventsListener")
    system.eventStream.subscribe(eventListener.ref, classOf[CircuitBreakerEvent])

    "be attached with talos events reporting" in {
      circuitBreakerWithEventStreamReporting.callWithSyncCircuitBreaker(() => 1)
      eventListener.expectMsgType[CircuitBreakerEvent]
    }
    "publish events for successful calls" in {
      circuitBreakerWithEventStreamReporting.callWithSyncCircuitBreaker(() => 1)
      val successfulCall = eventListener.expectMsgType[SuccessfulCall]
      successfulCall should matchPattern {
        case SuccessfulCall("testCircuitBreaker", elapsedTime) if elapsedTime > (0 nanos) =>
      }
    }
    "publish events for failures" in {
      Try(circuitBreakerWithEventStreamReporting.callWithSyncCircuitBreaker(() => throw new RuntimeException))
      val failureCall = eventListener.expectMsgType[CallFailure]
      failureCall should matchPattern {
        case CallFailure("testCircuitBreaker", elapsedTime) if elapsedTime > (0 nanos) =>
      }
    }

    "publish events for timeouts" in {
      Try(circuitBreakerWithEventStreamReporting.callWithSyncCircuitBreaker(() => Thread.sleep(2000)))
      val timeoutCall = eventListener.expectMsgType[CallTimeout]
      timeoutCall should matchPattern {
        case CallTimeout("testCircuitBreaker", elapsedTime) if elapsedTime >= (1 seconds) =>
      }
    }

    "publish events on open circuit" in {
      circuitBreakerWithEventStreamReporting.callWithSyncCircuitBreaker(() => 1)
      eventListener.expectMsgType[SuccessfulCall]
      for (i <- 1 to 5) {
        val failedCall =
          Try(circuitBreakerWithEventStreamReporting.callWithSyncCircuitBreaker(() => throw new RuntimeException))
        eventListener.expectMsgType[CallFailure]
      }
      eventListener.expectMsg(CircuitOpen(circuitBreakerName))
    }
    "publish events short circuited calls" in {
      Try(circuitBreakerWithEventStreamReporting.callWithSyncCircuitBreaker(() => throw new RuntimeException))
      eventListener.expectMsgType[ShortCircuitedCall]
    }
    "publish events on half open" in{
      Thread.sleep(5000)
      eventListener.expectMsg(CircuitHalfOpen(circuitBreakerName))
    }

    "publish events on closed" in{
      circuitBreakerWithEventStreamReporting.callWithSyncCircuitBreaker(() => 1)
      eventListener.expectMsgType[SuccessfulCall]
      eventListener.expectMsg(CircuitClosed(circuitBreakerName))
    }
  }

}
