package talos.laws

import cats.effect.{Effect, IO}
import org.scalatest.Matchers
import talos.events.TalosEvents.model._

import scala.util.Try

trait StateLaws[S, C, F[_]] extends CircuitBreakerSpec[C, F] with EventBusLaws[S] with Matchers {

  private[laws] def exposesCircuitOpenEvent(implicit F: Effect[F]) = {
    val failure = F.liftIO {
      IO {
        throw new RuntimeException
      }
    }

    val event = Stream.iterate(failure)(identity).map(
      unsafeCall => Try(run(unsafeCall))
    ).map(_ => acceptMsg)
      .dropWhile(_.isInstanceOf[CallFailure])
      .headOption

    event match {
      case Some(c: ShortCircuitedCall) =>
        c shouldBe ShortCircuitedCall(talosCircuitBreaker.name)
        Try(run(failure))
        acceptMsg shouldBe CircuitOpen(talosCircuitBreaker.name)
      case Some(c: CircuitOpen) =>
        c shouldBe CircuitOpen(talosCircuitBreaker.name)
        Try(run(failure))
        acceptMsg shouldBe ShortCircuitedCall(talosCircuitBreaker.name)
      case _ =>
        fail("Circuit breaker should have eventually been opened")
    }

  }

  private[laws] def exposesCircuitClosedTransition(implicit F: Effect[F]) = {
    val success = F.unit

    val eventualSuccess = Stream.iterate(success)(identity).map(
      unsafeCall => Try(run(unsafeCall))
    ).dropWhile(_.isFailure)
    .map(_ => acceptMsg)
      .dropWhile(_.isInstanceOf[ShortCircuitedCall])
      .headOption

    eventualSuccess match {
      case Some(c: CircuitHalfOpen) =>
        c shouldBe CircuitHalfOpen(talosCircuitBreaker.name)
        matchClosing(acceptMsg)
      case Some(SuccessfulCall(circuitBreakerName, _)) =>
        circuitBreakerName shouldBe talosCircuitBreaker.name
        acceptMsg shouldBe CircuitHalfOpen(talosCircuitBreaker.name)
        matchClosing(acceptMsg)
      case other =>
        fail(s"Circuit breaker should have eventually been closed, instead it was $other")
    }
  }

  private[this] def matchClosing(event: CircuitBreakerEvent) = event match {
    case c: CircuitClosed =>
      c shouldBe CircuitClosed(talosCircuitBreaker.name)
      acceptMsg.circuitBreakerName shouldBe talosCircuitBreaker.name
      acceptMsg.asInstanceOf[SuccessfulCall]
    case SuccessfulCall(name, _) =>
      name shouldBe acceptMsg.circuitBreakerName
      acceptMsg shouldBe CircuitClosed(talosCircuitBreaker.name)
    case other =>
      fail(s"Circuit breaker should have eventually been closed but it was $other")
  }
}
