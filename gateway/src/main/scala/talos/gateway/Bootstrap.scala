package talos.gateway

import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import kamon.Kamon
import talos.kamon.StatsAggregator

import scala.concurrent.duration._

object Bootstrap extends App {

  sealed trait GuardianProtocol
  private def guardianBehaviour: Behavior[GuardianProtocol] = Behaviors.setup {
    ctx =>
      val serverBinding = GatewayServer()(ctx)

      StatsAggregator.kamon()(ctx).unsafeToFuture()

      Behaviors.receiveMessage[GuardianProtocol] {
        case _ => Behaviors.ignore
      }.receiveSignal {
        case (ctx, PostStop) =>
          Kamon.stopModules()
          serverBinding.flatMap(_.terminate(30 seconds).map(println)(ctx.executionContext))(ctx.executionContext)
          Behaviors.stopped
      }
  }

  implicit val actorSystem = ActorSystem(guardianBehaviour, "TalosGateway")



  sys.addShutdownHook {
    actorSystem.terminate()
  }
}
