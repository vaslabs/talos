package talos.gateway

import io.gatling.core.Predef.scenario
import io.gatling.core.scenario.Simulation
import io.gatling.http.Predef.http
import io.gatling.core.Predef._
import scala.concurrent.duration._
class ContinuousTrafficHappySimulation extends Simulation{
  import BasicSimulation._

  before {
    dogServiceStub
    Bootstrap.main(Array())
  }

  val trafficRepeat = repeat(10) {
    exec(http("dog-service").get("/dogs")).pause(50 millis)
  }

  val scn = scenario("ContinuousTrafficHappySimulation")
    .exec(trafficRepeat)
    .inject(rampUsersPerSec(10).to(32).during(2 minutes))


  setUp(
    scn
  ).protocols(httpProtocol)

  after {
    wireMockServer.stop()
  }
}
