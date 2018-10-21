package talos.http

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import kamon.Kamon
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
class BootstrapSpec extends FlatSpec with Matchers{



}

class CircuitBreakerActivity(implicit actorSystem: ActorSystem) {
  import talos.events.syntax._
  lazy val circuitBreaker = CircuitBreaker.withEventReporting(
    "testCircuitBreaker",
    actorSystem.scheduler,
    5,
    2 seconds,
    5 seconds
  )

}
