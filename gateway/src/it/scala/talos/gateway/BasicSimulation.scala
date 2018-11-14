package talos.gateway

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import io.gatling.core.Predef._
import io.gatling.http.Predef._

object BasicSimulation {
  val httpProtocol = http // 4
    .baseUrl("http://0.0.0.0:8080") // 5
    .doNotTrackHeader("1")
    .userAgentHeader("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0")

  val Host = "localhost"

  val Port = 9000

  val wireMockServer = new WireMockServer(wireMockConfig().port(Port))

  lazy val dogServiceStub: Unit = {
    wireMockServer.start()
    stubFor(get(urlEqualTo(path))
      .willReturn(
        aResponse()
          .withStatus(200)))
  }
  WireMock.configureFor(Host, Port)

  val path = "/animals/dogs/"

}

class BasicSimulation extends Simulation {

  import BasicSimulation._


  before {
    dogServiceStub
    Bootstrap.main(Array())
  }



  val scn = scenario("BasicSimulation") // 7
    .exec(http("dog-service") // 8
    .get("/dogs")) // 9
    .pause(5)

  val setup = setUp( // 11
    scn.inject(atOnceUsers(32)) // 12
  ).protocols(httpProtocol)

  after {
    wireMockServer.stop()
  }


}
