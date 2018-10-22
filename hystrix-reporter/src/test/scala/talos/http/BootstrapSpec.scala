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
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Random

object BootstrapSpec extends App {

    implicit val actorSystem: ActorSystem = ActorSystem("BootstrapSpec")
    val statsAggregator =
      actorSystem.toTyped.systemActorOf(StatsAggregator.behavior(), "StatsAggregator")(Timeout(3 seconds))


    val statsGatherer = actorSystem.actorOf(CircuitBreakerStatsActor.props)
    implicit val clock: Clock = Clock.systemUTC()


    import scala.concurrent.ExecutionContext.Implicits.global
    val httpRouter: ServerEventHttpRouter =
      new CircuitBreakerEventsSource(2 seconds, statsGatherer) with ServerEventHttpRouter


    implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
    val startingServer = for {
      aggregator <- statsAggregator
      gatherer = statsGatherer
      _ = actorSystem.eventStream.subscribe(aggregator.toUntyped, classOf[CircuitBreakerEvent])
      reporter = new HystrixReporter(gatherer)
      _ = Kamon.addReporter(reporter)
      router = httpRouter
      serverStart <- Http().bindAndHandle(router.route, "0.0.0.0", 8080)
    } yield serverStart

//    import akka.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling._
//
//
//    import akka.http.scaladsl.model._

    sys.addShutdownHook {
      actorSystem.terminate()
      startingServer.map(_.unbind())
    }
    val activity = startCircuitBreakerActivity()

//    val streaming = Http()
//      .singleRequest(model.HttpRequest(uri = "http://0.0.0.0:8000/hystrix.stream"))
//      .flatMap(Unmarshal(_).to[Source[ServerSentEvent, NotUsed]])
//      .map(_.runForeach(println))
//


  def startCircuitBreakerActivity()(implicit actorSystem: ActorSystem): Future[Unit] = {
    val foo = Utils.createCircuitBreaker("foo")
    val bar = Utils.createCircuitBreaker("bar")
    implicit val executionContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
    Future {
      while (true) {
        foo.callWithSyncCircuitBreaker(() => Thread.sleep(Random.nextInt(100)))
        bar.callWithSyncCircuitBreaker(() => Thread.sleep(Random.nextInt(50)))
      }
    }
  }


}
