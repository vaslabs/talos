package talos.examples

import java.time.Clock
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.util.Timeout
import talos.http.HystrixReporterServer

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Random, Try}
import scala.concurrent.duration._

object ExampleApp extends App {

    implicit val actorSystem: ActorSystem = ActorSystem("TalosExample")

    implicit val clock: Clock = Clock.systemUTC()

    implicit val actorSystemTimeout: Timeout = Timeout(2 seconds)

    val server = new HystrixReporterServer("0.0.0.0", 8080)

    val startingServer = server.start(clock)

    sys.addShutdownHook {
      import ExecutionContext.Implicits.global
      actorSystem.terminate()
      startingServer.map(_.unbind())
      ()
    }
    val activity = startCircuitBreakerActivity()


    def startCircuitBreakerActivity()(implicit actorSystem: ActorSystem): Future[Unit] = {
      val foo = Utils.createCircuitBreaker("foo")
      val bar = Utils.createCircuitBreaker("bar")
      implicit val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))
      Future {
        while (true) {
          Try(bar.callWithSyncCircuitBreaker(() => Thread.sleep(Random.nextInt(50).toLong)))
        }
      }
      Future {
        while (true) {
          Try(foo.callWithSyncCircuitBreaker(() => Thread.sleep(Random.nextInt(100).toLong)))
        }
      }
      Future {
        while (true) {
          Thread.sleep(20000)
          if (Random.nextDouble() < 0.5) {
              for (i <- 1 to 10) yield Try(bar.callWithSyncCircuitBreaker(() => throw new RuntimeException))
          } else {
              for (i <- 1 to 10) yield Try(foo.callWithSyncCircuitBreaker(() => throw new RuntimeException))
          }
        }
      }
    }


}
