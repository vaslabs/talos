package talos.examples

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.pattern.CircuitBreaker
import talos.circuitbreakers.akka.AkkaCircuitBreaker

object Utils {

  import scala.concurrent.duration._

  def createCircuitBreaker(name: String = "testCircuitBreaker")
                          (implicit system: ActorSystem[_]): AkkaCircuitBreaker.Instance = {
    AkkaCircuitBreaker(
      name,
      CircuitBreaker(
        system.scheduler.toClassic,
        5,
        2 seconds,
        5 seconds
      )
    )
  }

}