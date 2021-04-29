package talos.circuitbreakers.monix

import cats.effect.IO
import monix.catnap.CircuitBreaker

import scala.concurrent.duration._

class SyntaxSpec {

  def implicitResolution: MonixCircuitBreaker.Instance[IO] = {
    implicit def contextShift: ContextShift[IO] = ???
    def circuitBreaker: CircuitBreaker[IO] = ???
    MonixCircuitBreaker(
      "testCircuitBreaker",
      circuitBreaker,
      4 seconds
    )
  }
}
