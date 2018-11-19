package talos.gateway

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, StatusCodes, Uri}
import akka.testkit.TestKit
import cats.effect.IO
import cats.implicits._
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._

class GatewayServerSpec extends TestKit(ActorSystem("GatewayServerSpec")) with FlatSpecLike with Matchers with BeforeAndAfterAll {


  val port = 9000
  val wireMockServer = new WireMockServer(wireMockConfig().port(port))
  val gatewayServer = GatewayServer()

  override def beforeAll(): Unit = {
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
  }

  override def afterAll(): Unit = {
    wireMockServer.stop()
    val termination = IO(gatewayServer.map(_.terminate(5 seconds))) *> IO(system.terminate())
    (IO.fromFuture(termination) *> IO.unit).unsafeRunSync()
  }

  "gateway server" can "accept requests" in {
    Http().singleRequest(HttpRequest(uri = Uri("http://localhost:8080/dogs"))).map(
      _.status shouldBe StatusCodes.OK
    )
  }

  "gateway server" can "forward wild card remaining paths" in {
    Http().singleRequest(HttpRequest(uri = Uri("http://localhost:8080/cat/1"))).map(
      _.status shouldBe StatusCodes.OK
    )
  }

}
