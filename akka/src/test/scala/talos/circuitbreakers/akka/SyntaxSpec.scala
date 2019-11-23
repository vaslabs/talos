package talos.circuitbreakers.akka

import akka.actor.typed.ActorSystem

import scala.concurrent.duration._
class SyntaxSpec {

  def implicitResolution: AkkaCircuitBreaker.Instance = {
    implicit def ctx: ActorSystem[_] = ???
    AkkaCircuitBreaker(
      "testCircuitBreaker",
      5,
      5 seconds,
      10 seconds
    )
  }
}
