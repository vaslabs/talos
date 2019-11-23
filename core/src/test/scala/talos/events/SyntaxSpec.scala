package talos.events

import talos.circuitbreakers
import talos.circuitbreakers.{EventBus, Talos, TalosCircuitBreaker}

class SyntaxSpec {

  type Id[A] = A

  val dummyCircuitBreaker = ()

  def noOpEventBus: EventBus[Nothing] = ???

  implicit val dummyTalosCircuitBreaker = new TalosCircuitBreaker[Unit, Nothing, Id] {
    override def name: String = "dummy"

    override def protect[A](task: Id[A]): Id[A] = task

    override def circuitBreaker: Id[Unit] = ()

    override def protectWithFallback[A, E](task: Id[A], fallback: Id[E]): Id[Either[E, A]] = Right(task)

    override def eventBus: circuitbreakers.EventBus[Nothing] = noOpEventBus
  }

  Talos.circuitBreaker[Unit, Nothing, Id]



}
