package talos.gateway

import io.gatling.core.Predef._
import io.gatling.http.Predef._

object BasicSimulation {
  val httpProtocol = http // 4
    .baseUrl("http://localhost:8080") // 5
    .doNotTrackHeader("1")
    .userAgentHeader("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0")

}

class BasicSimulation extends Simulation {

  import BasicSimulation._


  val scn = scenario("BasicSimulation") // 7
    .exec(http("dogs-service") // 8
    .get("/animals/dogs")) // 9
    .pause(5)

  val setup = setUp( // 11
    scn.inject(atOnceUsers(32)) // 12
  ).protocols(httpProtocol)


}
