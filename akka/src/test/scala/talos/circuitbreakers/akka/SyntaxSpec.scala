package talos.circuitbreakers.akka

import akka.actor.ActorSystem

import scala.concurrent.duration._
class SyntaxSpec {

  def implicitResolution: AkkaCircuitBreaker.Instance = {
    implicit def actorSystem: ActorSystem = ???

    AkkaCircuitBreaker(
      "testCircuitBreaker",
      5,
      5 seconds,
      10 seconds
    )
  }
}
