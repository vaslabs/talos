package talos.events

import talos.circuitbreakers.{Talos, TalosCircuitBreaker}

class SyntaxSpec {

  type Id[A] = A

  val dummyCircuitBreaker = ()

  implicit val dummyTalosCircuitBreaker = new TalosCircuitBreaker[Unit, Id] {
    override def name: String = "dummy"

    override def protect[A](task: Id[A]): Id[A] = task

    override def circuitBreaker: Id[Unit] = ()

    override def protectWithFallback[A, E](task: Id[A], fallback: Id[E]): Id[Either[E, A]] = Right(task)
  }

  Talos.circuitBreaker[Unit, Id]
}
