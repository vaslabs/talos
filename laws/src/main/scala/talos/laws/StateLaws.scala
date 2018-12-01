package talos.laws

import cats.effect.{Effect, IO}
import org.scalatest.Matchers
import talos.events.TalosEvents.model._

import scala.util.Try

trait StateLaws[S, C, F[_]] extends CircuitBreakerSpec[C, F] with EventBusLaws[S] with Matchers {

  private[laws] def exposesCircuitOpenEvent(implicit F: Effect[F]) = {
    var failures = 0
    val failure = F.liftIO {
      IO {
        failures+=1
        throw new RuntimeException
      }
    }

    val event = Stream.iterate(failure)(identity).map(
      unsafeCall => Try(run(unsafeCall))
    ).map(_ => acceptMsg)
      .dropWhile{ event =>
        if (event.isInstanceOf[CallFailure]) {
          failures -= 1
          true
        }
        else
          false
      }
      .headOption

    event match {
      case Some(c: ShortCircuitedCall) =>
        c shouldBe ShortCircuitedCall(talosCircuitBreaker.name)
        Try(run(failure))
        acceptMsg shouldBe CircuitOpen(talosCircuitBreaker.name)
      case Some(c: CircuitOpen) =>
        c shouldBe CircuitOpen(talosCircuitBreaker.name)
        if (failures == 1) {
          acceptMsg.asInstanceOf[CallFailure]
        } else if (failures > 1)
          fail("Circuit breaker missed a lot of failure events")

        Try(run(failure))
        acceptMsg shouldBe ShortCircuitedCall(talosCircuitBreaker.name)
      case _ =>
        fail("Circuit breaker should have eventually been opened")
    }

  }

  private[laws] def exposesCircuitClosedTransition(implicit F: Effect[F]) = {
    val success = F.unit

    Try(run(success))
    acceptMsg shouldBe ShortCircuitedCall(talosCircuitBreaker.name)

    val eventualSuccess = Stream.iterate(success)(identity).map(
      unsafeCall => Try(run(unsafeCall))
    ).dropWhile(_.isFailure)
      .map(_ => acceptMsg)
      .dropWhile(_.isInstanceOf[ShortCircuitedCall])
      .headOption

    val nextEvents: List[CircuitBreakerEvent] = List(eventualSuccess.get, acceptMsg, acceptMsg)

    nextEvents.find(_.isInstanceOf[SuccessfulCall]) match {
      case None => fail ("A successful call event was expected")
      case _ =>
        nextEvents should contain(CircuitClosed(talosCircuitBreaker.name))
        nextEvents should contain(CircuitHalfOpen(talosCircuitBreaker.name))
    }


  }

}
