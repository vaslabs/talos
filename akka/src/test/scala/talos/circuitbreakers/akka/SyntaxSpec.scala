package talos.circuitbreakers.akka

import akka.actor.typed.ActorSystem

import scala.concurrent.duration._
import scala.reflect.ClassTag
class SyntaxSpec {

  def implicitResolution[T](implicit classTag: ClassTag[T]): AkkaCircuitBreaker.Instance[T] = {
    implicit def ctx: ActorSystem[_] = ???
    AkkaCircuitBreaker[T](
      "testCircuitBreaker",
      5,
      5 seconds,
      10 seconds
    )
  }
}
