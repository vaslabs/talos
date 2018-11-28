package talos.laws

import cats.effect.Effect
import talos.circuitbreakers.TalosCircuitBreaker

import scala.concurrent.duration.FiniteDuration

private[laws] trait CircuitBreakerSpec[C, F[_]] {
  val talosCircuitBreaker: TalosCircuitBreaker[C, F]

  val callTimeout: FiniteDuration

  private[laws] def run[A](unprotectedCall: F[A])(implicit F: Effect[F]): A = F.toIO(
    talosCircuitBreaker.protect(unprotectedCall)
  ).unsafeRunSync()
}
