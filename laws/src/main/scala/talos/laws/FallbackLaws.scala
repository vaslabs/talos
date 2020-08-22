package talos.laws

import cats.effect.{Effect, IO}
import talos.events.TalosEvents.model._
import org.scalacheck.Gen

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Try
import org.scalatest.matchers.should.Matchers

trait FallbackLaws[C, S, F[_]] extends EventBusLaws[S] with CircuitBreakerSpec[C, S, F] with Matchers {

  private[laws] def fallbackActivatedOnError(implicit F: Effect[F]) = {
    val error = Gen.alphaNumStr
    runWithFallback(F.raiseError(new RuntimeException), F.pure(error)) should matchPattern {
      case Left(err) if error == err =>
    }
    acceptMsg.isInstanceOf[CallFailure]
    acceptMsg shouldBe FallbackSuccess(talosCircuitBreaker.name)
  }

  private[laws] def fallbackFailureIsLogged(implicit F: Effect[F]) = {
    Try(runWithFallback(F.raiseError(new RuntimeException), F.raiseError(new IllegalStateException)))
    acceptMsg.isInstanceOf[CallFailure]
    acceptMsg shouldBe FallbackFailure(talosCircuitBreaker.name)
  }

  private[laws] def fallbackExpected(implicit F: Effect[F]) = {
    val error = Gen.alphaNumStr
    runWithFallback(F.unit, F.pure(error)) should matchPattern {
      case Left(err) if error == err =>
    }
    acceptMsg match {
      case ShortCircuitedCall(name) if name == talosCircuitBreaker.name =>
        acceptMsg shouldBe FallbackSuccess(talosCircuitBreaker.name)
      case FallbackSuccess(name) if name == talosCircuitBreaker.name =>
        acceptMsg shouldBe ShortCircuitedCall(talosCircuitBreaker.name)
      case other => fail(s"Unexpected circuit breaker event $other")
    }

  }


  private[laws] def fallbackSlownessIsNotAllowed(implicit F: Effect[F]) = {
    implicit val timerIO = IO.timer(ExecutionContext.global)
    Try(runWithFallback(F.raiseError(new RuntimeException), F.liftIO(IO.sleep(2 seconds))))
    acceptMsg.isInstanceOf[CallFailure]
    acceptMsg shouldBe FallbackRejected(talosCircuitBreaker.name)
  }
}
