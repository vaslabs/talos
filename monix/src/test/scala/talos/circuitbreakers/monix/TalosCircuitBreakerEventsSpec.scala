package talos.circuitbreakers.monix

import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import monix.eval.Task
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import talos.events.TalosEvents.model._

import scala.concurrent.duration._
import scala.util.Try

class TalosCircuitBreakerEventsSpec extends
      TestKit(ActorSystem("AkkaCircuitBreakerEvents"))
      with WordSpecLike
      with Matchers
      with BeforeAndAfterAll{

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  "a circuit breaker" can {
    val circuitBreakerName = "testCircuitBreaker"

    val circuitBreakerWithEventStreamReporting = MonixCircuitBreaker(
      circuitBreakerName,
      maxFailures = 5,
      callTimeout = 1 second,
      resetTimeout = 5 seconds
    )


    val eventListener = TestProbe("talosEventsListener")
    system.eventStream.subscribe(eventListener.ref, classOf[CircuitBreakerEvent])

    "be attached with talos events reporting" in {
      circuitBreakerWithEventStreamReporting.protectUnsafe(Task(1))
      eventListener.expectMsgType[CircuitBreakerEvent]
    }
    "publish events for successful calls" in {
      circuitBreakerWithEventStreamReporting.protectUnsafe(Task(1))
      val successfulCall = eventListener.expectMsgType[SuccessfulCall]
      successfulCall should matchPattern {
        case SuccessfulCall("testCircuitBreaker", elapsedTime) if elapsedTime > (0 nanos) =>
      }
    }
    "publish events for failures" in {
      Try(circuitBreakerWithEventStreamReporting.protectUnsafe(
        Task.delay {throw new RuntimeException})
      )
      val failureCall = eventListener.expectMsgType[CallFailure]
      failureCall should matchPattern {
        case CallFailure("testCircuitBreaker", elapsedTime) if elapsedTime > (0 nanos) =>
      }
    }

    val taskTimeout = Task({
      Thread.sleep(2000)
      throw new TimeoutException()
    })
    "publish events for timeouts" in {
      Try(circuitBreakerWithEventStreamReporting.protectUnsafe(taskTimeout))
      val timeoutCall = eventListener.expectMsgType[CallTimeout]
      timeoutCall should matchPattern {
        case CallTimeout("testCircuitBreaker", elapsedTime) if elapsedTime >= (1 seconds) =>
      }
    }

    "publish events on open circuit" in {
      circuitBreakerWithEventStreamReporting.protectUnsafe(Task(1))
      eventListener.expectMsgType[SuccessfulCall]
      for (_ <- 1 to 5) {
        Try(circuitBreakerWithEventStreamReporting.protectUnsafe(Task(throw new RuntimeException)))
        eventListener.expectMsgType[CallFailure]
      }
      eventListener.expectMsg(CircuitOpen(circuitBreakerName))
    }
    "publish events short circuited calls" in {
      Try(circuitBreakerWithEventStreamReporting.protectUnsafe(Task(throw new RuntimeException)))
      eventListener.expectMsgType[ShortCircuitedCall]
    }
    "publish events on half open" in{
      Thread.sleep(5000)
      circuitBreakerWithEventStreamReporting.protectUnsafe(Task(1))
      eventListener.expectMsg(CircuitHalfOpen(circuitBreakerName))
    }

    "publish events on closed" in{
      eventListener.expectMsg(CircuitClosed(circuitBreakerName))
      eventListener.expectMsgType[SuccessfulCall]
    }
  }

}
