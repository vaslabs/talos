package talos.examples

import java.time.Clock
import java.util.concurrent.Executors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import cats.effect.IO
import kamon.Kamon

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Random, Try}

object ExampleApp extends App {

  Kamon.init()
  sealed trait GuardianProtocol
  def guardianBehaviour: Behavior[GuardianProtocol] = Behaviors.setup {
    ctx =>
    implicit val actorContext = ctx

      startCircuitBreakerActivity()

      Behaviors.receive[GuardianProtocol] {
      case _ => Behaviors.ignore
    }.receiveSignal {
      case (_, PostStop) =>
        Behaviors.stopped
    }
  }

    implicit val actorSystem: ActorSystem[_] = ActorSystem(guardianBehaviour, "TalosExample")

    implicit val TestClock: Clock = Clock.systemUTC()


    sys.addShutdownHook {
      actorSystem.terminate()
    }



    def startCircuitBreakerActivity()(implicit actorContext: ActorContext[_]): Future[Unit] = {
      val foo = Utils.createCircuitBreaker("foo")
      val bar = Utils.createCircuitBreaker("bar")
      val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))
      Future {
        while (true) {
          Try(bar.protect(IO(Thread.sleep(Random.nextInt(50).toLong))).unsafeRunSync())
        }
      }(executionContext)
      Future {
        while (true) {
          Try(foo.protect(IO(Thread.sleep(Random.nextInt(100).toLong))).unsafeRunSync())
        }
      }(executionContext)
      Future {
        while (true) {
          Thread.sleep(20000)
          if (Random.nextDouble() < 0.5) {
              for (i <- 1 to 10) yield Try(bar.protect(IO(throw new RuntimeException)).unsafeRunSync())
          } else {
              for (i <- 1 to 10) yield Try(foo.protect(IO(throw new RuntimeException)).unsafeRunSync())
          }
        }
      }(executionContext)
    }


}
