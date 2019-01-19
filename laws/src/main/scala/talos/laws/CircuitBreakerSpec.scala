package talos.laws

import cats.effect.{Effect}
import org.scalacheck.Gen
import talos.circuitbreakers.TalosCircuitBreaker

import scala.concurrent.duration._

private[laws] trait CircuitBreakerSpec[C, F[_]] {
  val talosCircuitBreaker: TalosCircuitBreaker[C, F]

  final val callTimeout: FiniteDuration = Gen.oneOf( 4 seconds, 5 seconds, 6 seconds).sample.get

  private[laws] def run[A](unprotectedCall: F[A])(implicit F: Effect[F]): A = F.toIO(
    talosCircuitBreaker.protect(unprotectedCall)
  ).unsafeRunSync()

  private[laws] def runWithFallback[A, E](unprotectedCall: F[A], fallback: F[E])(implicit F: Effect[F]): Either[E, A] =
    F.toIO(talosCircuitBreaker
      .protectWithFallback(unprotectedCall, fallback))
    .unsafeRunSync()

}
