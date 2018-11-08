package talos.events

import talos.circuitbreakers.{Talos, TalosCircuitBreaker}

class SyntaxSpec {

  type Id[A] = A

  val dummyCircuitBreaker = ()

  implicit val dummyTalosCircuitBreaker = new TalosCircuitBreaker[Unit, Id] {
    override def name: String = "dummy"

    override def protect[A](task: Id[A]): Id[A] = task

    override def circuitBreaker: Id[Unit] = ()
  }

  Talos.circuitBreaker[Unit, Id]
}
