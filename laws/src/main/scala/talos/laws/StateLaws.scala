package talos.laws

import cats.effect.{Effect, IO}
import org.scalatest.Matchers
import talos.events.TalosEvents.model._

import scala.concurrent.TimeoutException
import scala.util.Try
import talos.internal.crosscompat.streams._
trait StateLaws[C, S, F[_]] extends CircuitBreakerSpec[C, S, F] with EventBusLaws[S] with Matchers {

  def callsAreShortCircuitedFromNowOn(implicit F: Effect[F]) = {
    Try(run(F.pure(())))
    acceptMsg shouldBe ShortCircuitedCall(talosCircuitBreaker.name)
  }

  private[laws] def exposesCircuitOpenEvent(implicit F: Effect[F]) = {
    var failures = 0
    val failure = F.liftIO {
      IO.defer {
        failures+=1
        IO.raiseError(new RuntimeException)
      }
    }

    val event = LazyList.iterate(failure)(identity).map(
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
        acceptMsg match {
          case ShortCircuitedCall(_) =>
          case CallFailure(_, _) =>
            maybeAccept(ShortCircuitedCall(talosCircuitBreaker.name))
          case _ => fail("Missing an event to indicate that the call was short circuited")
        }

      case _ =>
        fail("Circuit breaker should have eventually been opened")
    }
  }

  private def maybeAccept(call: ShortCircuitedCall) =
    Try {acceptMsg shouldBe call}.toEither.left.map {
      _ should matchPattern {
        case _: TimeoutException =>
      }
    }.merge


  private[laws] def exposesCircuitClosedTransition(implicit F: Effect[F]) = {
    val success = F.unit

    Try(run(success))
    acceptMsg shouldBe ShortCircuitedCall(talosCircuitBreaker.name)

    val eventualSuccess = LazyList.iterate(success)(identity).map(
      unsafeCall => Try(run(unsafeCall))
    ).dropWhile(_.isFailure)
      .map(_ => acceptMsg)
      .dropWhile(_.isInstanceOf[ShortCircuitedCall])
      .headOption

    val nextEvents: List[CircuitBreakerEvent] = List(eventualSuccess.get, acceptMsg, acceptMsg, acceptMsg)

    nextEvents.find(_.isInstanceOf[SuccessfulCall]) match {
      case None => fail ("A successful call event was expected")
      case _ =>
        nextEvents should contain(CircuitClosed(talosCircuitBreaker.name))
        nextEvents should contain(CircuitHalfOpen(talosCircuitBreaker.name))
    }

  }

}