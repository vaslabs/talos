package talos.circuitbreakers.monix

import akka.actor.ActorSystem
import cats.effect.{ContextShift, IO, Timer}
import monix.catnap.CircuitBreaker
import talos.circuitbreakers.TalosCircuitBreaker

import scala.concurrent.duration._

class SyntaxSpec {

  def implicitResolution: TalosCircuitBreaker[CircuitBreaker[IO], IO] = {
    implicit def actorSystem: ActorSystem = ???
    implicit def contextShift: ContextShift[IO] = ???
    implicit def timer: Timer[IO] = ???
    def circuitBreaker: CircuitBreaker[IO] = ???
    MonixCircuitBreaker(
      "testCircuitBreaker",
      circuitBreaker,
      4 seconds
    )
  }
}
