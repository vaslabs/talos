package talos.gateway

import io.gatling.core.Predef.scenario
import io.gatling.core.scenario.Simulation
import io.gatling.http.Predef.http
import io.gatling.core.Predef._
import scala.concurrent.duration._
class ContinuousTrafficHappySimulation extends Simulation{
  import BasicSimulation._


  val trafficRepeat = repeat(10) {
    exec(http("dog-service").get("/animals/dogs")).pause(50 millis)
  }

  val scn = scenario("ContinuousTrafficHappySimulation")
    .exec(trafficRepeat)
    .inject(rampUsersPerSec(10).to(32).during(2 minutes))


  setUp(
    scn
  ).protocols(httpProtocol)

}
