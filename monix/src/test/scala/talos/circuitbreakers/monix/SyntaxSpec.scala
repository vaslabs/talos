package talos.circuitbreakers.monix

import akka.actor.ActorSystem
import cats.effect.IO
import monix.catnap.CircuitBreaker
import talos.circuitbreakers.TalosCircuitBreaker

class SyntaxSpec {

  def implicitResolution: TalosCircuitBreaker[CircuitBreaker[IO], IO] = {
    implicit def actorSystem: ActorSystem = ???
    def circuitBreaker: CircuitBreaker[IO] = ???
    MonixCircuitBreaker(
      "testCircuitBreaker",
      circuitBreaker
    )
  }
}
