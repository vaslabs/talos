package talos.gateway

import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration._

object Bootstrap extends App {

  sealed trait GuardianProtocol
  private def guardianBehaviour: Behavior[GuardianProtocol] = Behaviors.setup {
    ctx =>
      val serverBinding = GatewayServer()(ctx)

      Behaviors.receiveMessage[GuardianProtocol] {
        case _ => Behaviors.ignore
      }.receiveSignal {
        case (ctx, PostStop) =>
          serverBinding.flatMap(_.terminate(30 seconds).map(println)(ctx.executionContext))(ctx.executionContext)
          Behaviors.stopped
      }
  }

  implicit val actorSystem = ActorSystem(guardianBehaviour, "TalosGateway")



  sys.addShutdownHook {
    actorSystem.terminate()
  }
}
