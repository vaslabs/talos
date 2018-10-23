package talos.http

import java.time.Clock
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import kamon.Kamon
import talos.events.TalosEvents.model.CircuitBreakerEvent
import talos.kamon.StatsAggregator
import talos.kamon.hystrix.HystrixReporter

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object BootstrapSpec extends App {

    implicit val actorSystem: ActorSystem = ActorSystem("BootstrapSpec")

    implicit val clock: Clock = Clock.systemUTC()

    implicit val actorSystemTimeout: Timeout = Timeout(2 seconds)

    val server = new HystrixReporterServer(1 second, "0.0.0.0", 8080)

    val startingServer = server.start(clock)

    sys.addShutdownHook {
      import ExecutionContext.Implicits.global
      actorSystem.terminate()
      startingServer.map(_.unbind())
    }
    val activity = startCircuitBreakerActivity()


    def startCircuitBreakerActivity()(implicit actorSystem: ActorSystem): Future[Unit] = {
      val foo = Utils.createCircuitBreaker("foo")
      val bar = Utils.createCircuitBreaker("bar")
      implicit val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))
      Future {
        while (true) {
          bar.callWithSyncCircuitBreaker(() => Thread.sleep(Random.nextInt(50)))
        }
      }
      Future {
        while (true) {
          foo.callWithSyncCircuitBreaker(() => Thread.sleep(Random.nextInt(100)))
        }
      }
    }


}
