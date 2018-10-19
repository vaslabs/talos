package talos.events

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker

class SyntaxSpec {

  import scala.concurrent.duration._
  import syntax._
  implicit def system: ActorSystem = ???

  def circuitBreaker = CircuitBreaker.withEventReporting(
    "myCircuitBreaker",
    system.scheduler,
    maxFailures = 5,
    callTimeout = 1 second,
    resetTimeout = 5 seconds
  )

}
