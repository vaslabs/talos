package talos.examples

import java.time.Clock
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.util.Timeout
import cats.effect.IO
import talos.http._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Random, Try}
import scala.concurrent.duration._
import cats.implicits._
object ExampleApp extends App {

    implicit val actorSystem: ActorSystem = ActorSystem("TalosExample")

    val clock: Clock = Clock.systemUTC()

    implicit val actorSystemTimeout: Timeout = Timeout(2 seconds)
    import actorSystem.dispatcher

    val hystrixReporterDirective = new HystrixReporterDirective().hystrixStreamHttpRoute
    val server = new StartServer("0.0.0.0", 8080)

    val startingServer = (hystrixReporterDirective andThen server.startHttpServer).run(Clock.systemUTC())


    sys.addShutdownHook {
      actorSystem.terminate()
      startingServer.map(_.unbind())
      ()
    }
    val activity = startCircuitBreakerActivity()


    def startCircuitBreakerActivity()(implicit actorSystem: ActorSystem): Future[Unit] = {
      val foo = Utils.createCircuitBreaker("foo")
      val bar = Utils.createCircuitBreaker("bar")
      val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))
      Future {
        while (true) {
          Try(bar.protectUnsafe(IO(Thread.sleep(Random.nextInt(50).toLong))))
        }
      }(executionContext)
      Future {
        while (true) {
          Try(foo.protectUnsafe(IO(Thread.sleep(Random.nextInt(100).toLong))))
        }
      }(executionContext)
      Future {
        while (true) {
          Thread.sleep(20000)
          if (Random.nextDouble() < 0.5) {
              for (i <- 1 to 10) yield Try(bar.protectUnsafe(IO(throw new RuntimeException)))
          } else {
              for (i <- 1 to 10) yield Try(foo.protectUnsafe(IO(throw new RuntimeException)))
          }
        }
      }(executionContext)
    }


}
