package talos.circuitbreakers.akka

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import akka.testkit.{TestKit, TestProbe}
import cats.effect.IO
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import talos.circuitbreakers.{Talos, TalosCircuitBreaker}
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

    implicit val akkaCircuitBreaker: TalosCircuitBreaker[CircuitBreaker, IO] = AkkaCircuitBreaker(
      circuitBreakerName,
      maxFailures = 5,
      callTimeout = 1 second,
      resetTimeout = 5 seconds
    )

    val circuitBreakerWithEventStreamReporting = Talos.circuitBreaker[CircuitBreaker, IO]

    val eventListener = TestProbe("talosEventsListener")
    system.eventStream.subscribe(eventListener.ref, classOf[CircuitBreakerEvent])

    "be attached with talos events reporting" in {
      circuitBreakerWithEventStreamReporting.protect(IO(1)).unsafeRunSync()
      eventListener.expectMsgType[CircuitBreakerEvent]
    }
    "publish events for successful calls" in {
      circuitBreakerWithEventStreamReporting.protect(IO(1)).unsafeRunSync()
      val successfulCall = eventListener.expectMsgType[SuccessfulCall]
      successfulCall should matchPattern {
        case SuccessfulCall("testCircuitBreaker", elapsedTime) if elapsedTime > (0 nanos) =>
      }
    }
    "publish events for failures" in {
      Try(circuitBreakerWithEventStreamReporting.protect(
        IO.delay {throw new RuntimeException}).unsafeRunSync()
      )
      val failureCall = eventListener.expectMsgType[CallFailure]
      failureCall should matchPattern {
        case CallFailure("testCircuitBreaker", elapsedTime) if elapsedTime > (0 nanos) =>
      }
    }

    "publish events for timeouts" in {
      Try(circuitBreakerWithEventStreamReporting.protect(IO.delay(Thread.sleep(2000))).unsafeRunSync())
      val timeoutCall = eventListener.expectMsgType[CallTimeout]
      timeoutCall should matchPattern {
        case CallTimeout("testCircuitBreaker", elapsedTime) if elapsedTime >= (1 seconds) =>
      }
    }

    "publish events on open circuit" in {
      circuitBreakerWithEventStreamReporting.protect(IO(1)).unsafeRunSync()
      eventListener.expectMsgType[SuccessfulCall]
      for (_ <- 1 to 5) {
        Try(circuitBreakerWithEventStreamReporting.protect(IO.delay(throw new RuntimeException)).unsafeRunSync())
        eventListener.expectMsgType[CallFailure]
      }
      eventListener.expectMsg(CircuitOpen(circuitBreakerName))
    }
    "publish events short circuited calls" in {
      Try(circuitBreakerWithEventStreamReporting.protect(IO.delay(throw new RuntimeException)).unsafeRunSync())
      eventListener.expectMsgType[ShortCircuitedCall]
    }
    "publish events on half open" in{
      Thread.sleep(5000)
      eventListener.expectMsg(CircuitHalfOpen(circuitBreakerName))
    }

    "publish events on closed" in{
      circuitBreakerWithEventStreamReporting.protect(IO(1)).unsafeRunSync()
      eventListener.expectMsgType[SuccessfulCall]
      eventListener.expectMsg(CircuitClosed(circuitBreakerName))
    }
  }

}
