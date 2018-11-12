package talos.circuitbreakers.monix

import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import cats.effect._
import monix.catnap.CircuitBreaker
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

    implicit val effectClock = Clock.create[IO]

    val circuitBreaker: CircuitBreaker[IO] = CircuitBreaker.of[IO](5, 5 seconds).unsafeRunSync()
    val circuitBreakerWithEventStreamReporting = MonixCircuitBreaker(
      circuitBreakerName,
      circuitBreaker
    )


    val eventListener = TestProbe("talosEventsListener")
    system.eventStream.subscribe(eventListener.ref, classOf[CircuitBreakerEvent])

    "be attached with talos events reporting" in {
      circuitBreakerWithEventStreamReporting.protect(IO(println("what"))).unsafeRunSync()
      eventListener.expectMsgType[CircuitBreakerEvent]
    }
    "publish events for successful calls" in {
      circuitBreakerWithEventStreamReporting.protect(IO(println("hi"))).unsafeRunSync()
      val successfulCall = eventListener.expectMsgType[SuccessfulCall]
      successfulCall should matchPattern {
        case SuccessfulCall("testCircuitBreaker", elapsedTime) if elapsedTime > (0 nanos) =>
      }
    }
    "publish events for failures" in {
      Try(circuitBreakerWithEventStreamReporting.protect(
          IO.delay {throw new RuntimeException}
        ).unsafeRunSync()
      )
      val failureCall = eventListener.expectMsgType[CallFailure]
      failureCall should matchPattern {
        case CallFailure("testCircuitBreaker", elapsedTime) if elapsedTime > (0 nanos) =>
      }
    }

    val taskTimeout = IO({
      Thread.sleep(2000)
      throw new TimeoutException()
    })
    "publish events for timeouts" in {
      Try(circuitBreakerWithEventStreamReporting.protect(taskTimeout).unsafeRunSync())
      val timeoutCall = eventListener.expectMsgType[CallTimeout]
      timeoutCall should matchPattern {
        case CallTimeout("testCircuitBreaker", elapsedTime) if elapsedTime >= (1 seconds) =>
      }
    }

    "publish events on open circuit" in {
      circuitBreakerWithEventStreamReporting.protect(IO(println("the"))).unsafeRunSync()
      eventListener.expectMsgType[SuccessfulCall]
      for (_ <- 1 to 4) {
        Try(circuitBreakerWithEventStreamReporting.protect(IO(throw new RuntimeException)).unsafeRunSync())
        eventListener.expectMsgType[CallFailure]
      }
      Try(circuitBreakerWithEventStreamReporting.protect(IO(throw new RuntimeException)).unsafeRunSync())
      eventListener.expectMsg(CircuitOpen(circuitBreakerName))
      eventListener.expectMsgType[CallFailure]
    }
    "publish events short circuited calls" in {
      Try(circuitBreakerWithEventStreamReporting.protect(IO(throw new RuntimeException)).unsafeRunSync())
      eventListener.expectMsgType[ShortCircuitedCall]
    }
    "publish events on half open" in{
      Thread.sleep(5000)
      circuitBreakerWithEventStreamReporting.protect(IO(1)).unsafeRunSync()
      eventListener.expectMsgType[CallFailure]
      eventListener.expectMsg(CircuitHalfOpen(circuitBreakerName))
    }

    "publish events on closed" in{
      eventListener.expectMsg(CircuitClosed(circuitBreakerName))
      eventListener.expectMsgType[SuccessfulCall]
    }
  }

}
