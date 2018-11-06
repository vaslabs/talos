package talos.circuitbreakers.akka

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import cats.effect.IO
import talos.circuitbreakers.TalosCircuitBreaker

import scala.concurrent.duration._
class SyntaxSpec {

  def implicitResolution: TalosCircuitBreaker[CircuitBreaker, IO] = {
    implicit def actorSystem: ActorSystem = ???

    AkkaCircuitBreaker(
      "testCircuitBreaker",
      5,
      5 seconds,
      10 seconds
    )
  }
}
