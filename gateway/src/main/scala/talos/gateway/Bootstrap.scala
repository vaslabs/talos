package talos.gateway

import akka.actor.ActorSystem
import cats.effect.IO

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Bootstrap extends App {

  implicit val actorSystem = ActorSystem("TalosGateway")
  val serverBinding = GatewayServer()


  sys.addShutdownHook {
    IO.fromFuture(IO {
      import ExecutionContext.Implicits._
      serverBinding.flatMap(_.terminate(5 seconds).map(println))
    }).unsafeRunSync()

  }
}
