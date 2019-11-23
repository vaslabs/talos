package talos.gateway

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, StatusCodes, Uri}
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}

class GatewayServerSpec extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  val testKit = ActorTestKit()

  val port = 9000
  val wireMockServer = new WireMockServer(wireMockConfig().port(port))
  sealed trait Ignore

  val gatewayGuardian: Behavior[Ignore] = Behaviors.setup {
    ctx =>
      implicit val actorContext = ctx
      GatewayServer()
      Behaviors.ignore
  }

  implicit val system = testKit.system.toClassic


  override def beforeAll(): Unit = {
    testKit.spawn(gatewayGuardian, "GatewayGuardian")

    wireMockServer.start()
    WireMock.configureFor("localhost", port)
    val path = "/animals/dogs/"
    stubFor(get(urlEqualTo(path))
      .willReturn(
        aResponse()
          .withStatus(200)))

    stubFor(get(urlEqualTo("/animals/cat/1"))
        .willReturn(aResponse().withStatus(200))
    )
    ()
  }

  override def afterAll(): Unit = {
    wireMockServer.stop()
    testKit.shutdownTestKit()
  }

  "gateway server" can "accept requests" in {
    Http().singleRequest(HttpRequest(uri = Uri("http://localhost:8080/dogs"))).map {
      r =>
        r.discardEntityBytes()
        r.status shouldBe StatusCodes.OK
    }
  }

  "gateway server" can "forward wild card remaining paths" in {
    Http().singleRequest(HttpRequest(uri = Uri("http://localhost:8080/cat/1"))).map {
      r =>
        r.discardEntityBytes()
        r.status shouldBe StatusCodes.OK
    }
  }

}
