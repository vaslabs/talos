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
      val distribution = 10
      val rndIdx = Random.nextInt(distribution*10)
      val path = (rndIdx % distribution) match {
        case 0 =>
          "/animals/cats"
        case 1 =>
          "/animals/cats"
        case 2 =>
          "/animals/cats"
        case 3 =>
          "/animals/dogs"
        case 4 =>
          "/animals/dogsb"
        case _ =>
          "/animals/catsb"
      }

      Map("service" -> path)
    }
  }

  val trafficRepeat = repeat(10) {
    feed(callCats1In5)
      .exec(http("multi-service").get(f"$${service}")).pause(100 milli)

  }

  val scn = scenario("MultiserviceTrafficHappySimulation")
    .exec(trafficRepeat)
    .inject(rampUsersPerSec(20).to(70).during(2 minutes))


  setUp(
    scn
  ).protocols(httpProtocol)

}
