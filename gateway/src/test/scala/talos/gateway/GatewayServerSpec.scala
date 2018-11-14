package talos.gateway

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, StatusCodes, Uri}
import akka.testkit.TestKit
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import org.scalatest.{AsyncFlatSpecLike, BeforeAndAfterAll, Matchers}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import com.github.tomakehurst.wiremock.client.WireMock._

class GatewayServerSpec extends TestKit(ActorSystem("GatewayServerSpec")) with AsyncFlatSpecLike with Matchers with BeforeAndAfterAll {

  val gatewayServer = GatewayServer()

  val port = 9000
  val wireMockServer = new WireMockServer(wireMockConfig().port(port))

  override def beforeAll(): Unit = {
    wireMockServer.start()
    WireMock.configureFor("localhost", port)
    val path = "/animals/dogs/"
    stubFor(get(urlEqualTo(path))
      .willReturn(
        aResponse()
          .withStatus(200)))
  }

  override def afterAll(): Unit = {
    wireMockServer.stop()
  }

  "gateway server" can "accept requests" in {
    Http().singleRequest(HttpRequest(uri = Uri("http://localhost:8080/dogs"))).map(
      _.status shouldBe StatusCodes.OK
    )
  }

}
