package talos.gateway

import io.gatling.core.Predef._
import io.gatling.core.scenario.Simulation
import io.gatling.http.Predef.http

import scala.concurrent.duration._
import scala.util.Random

class MultiserviceTrafficHappySimulation extends Simulation{
  import BasicSimulation._

  val callCats1In5 =  {
    Iterator.continually {
      val rndIdx = Random.nextInt(2)
      val path = if (rndIdx % 2 == 0)
        "/animals/cats"
      else
        "/animals/dogs"
      Map("service" -> path)
    }
  }

  val trafficRepeat = repeat(30) {
    feed(callCats1In5)
      .exec(http("multi-service").get(f"$${service}")).pause(50 milli)

  }

  val scn = scenario("MultiserviceTrafficHappySimulation")
    .exec(trafficRepeat)
    .inject(rampUsersPerSec(10).to(32).during(2 minutes))


  setUp(
    scn
  ).protocols(httpProtocol)

}
