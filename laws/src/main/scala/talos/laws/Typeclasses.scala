package talos.laws

import cats.effect.Effect
import talos.circuitbreakers.TalosCircuitBreaker

private[laws] trait Typeclasses[C, F[_]] {
  val talosCircuitBreaker: TalosCircuitBreaker[C, F]

  private[laws] def run[A](unprotectedCall: F[A])(implicit F: Effect[F]): A = F.toIO(
    talosCircuitBreaker.protect(unprotectedCall)
  ).unsafeRunSync()
}
