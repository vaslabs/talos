package talos.circuitbreakers.monix

import akka.actor.ActorSystem
import monix.eval.{Task, TaskCircuitBreaker}
import talos.circuitbreakers.TalosCircuitBreaker

import scala.concurrent.duration._

class SyntaxSpec {

  def implicitResolution: TalosCircuitBreaker[TaskCircuitBreaker, Task] = {
    implicit def actorSystem: ActorSystem = ???

    MonixCircuitBreaker(
      "testCircuitBreaker",
      5 seconds,
      5,
      5 seconds
    )
  }
}
