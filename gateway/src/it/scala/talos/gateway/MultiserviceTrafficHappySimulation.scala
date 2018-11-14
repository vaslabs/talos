package talos.gateway

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.gatling.core.Predef._
import io.gatling.core.scenario.Simulation
import io.gatling.http.Predef.http

import scala.concurrent.duration._
import scala.util.Random

class MultiserviceTrafficHappySimulation extends Simulation{
  import BasicSimulation._

  val catsPort = 9001
  val catsWireMockServer = new WireMockServer(wireMockConfig().port(catsPort))


  lazy val catsServiceStub: Unit = {
    catsWireMockServer.start()
    new WireMock("localhost", catsPort).register(
      get(urlEqualTo("/animals/cats/"))
        .willReturn(
          aResponse()
            .withStatus(200))
    )

  }

  before {
    catsServiceStub
    dogServiceStub
    Bootstrap.main(Array())
  }

  val callCats1In5 =  {
    Iterator.continually {
      val rndIdx = Random.nextInt(5)
      val path = if (rndIdx % 10 == 0)
        "/cats"
      else
        "/dogs"
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

  after {
    dogWireMockServer.stop()
    catsWireMockServer.stop()
  }
}
