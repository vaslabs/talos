package talos.gateway

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, StatusCodes, Uri}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.Materializer
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import pureconfig.ConfigSource
import talos.gateway.config.GatewayConfig

import scala.concurrent.Await
import scala.concurrent.duration._
import pureconfig.generic.auto._
import config.pureconfigExt._
import kamon.Kamon
class BulkheadingSpec extends FlatSpec
  with BeforeAndAfterAll
  with Matchers {

  private val configString =
    """
      |{
      |    services: [
      |      {
      |        secure: false,
      |        host: "localhost",
      |        port: 9002,
      |        mappings: [
      |          {
      |            gateway-path: "/animals/dogs",
      |            methods: [GET],
      |            target-path: "/dogs/"
      |          },
      |          {
      |            gateway-path: "/give/404",
      |            methods: [GET],
      |            target-path: "/unmatched/"
      |           }
      |        ],
      |        max-inflight-requests: 1,
      |        call-timeout: 5 seconds,
      |        importance: High
      |      },
      |      {
      |        secure: false,
      |        host: "localhost",
      |        port: 9003,
      |        mappings: [
      |          {
      |            gateway-path: "/vehicles/bikes",
      |            methods: [GET],
      |            target-path: "/bikes/"
      |          }
      |        ]
      |        max-inflight-requests: 4,
      |        call-timeout: 2 seconds,
      |        importance: Low
      |      }
      |    ],
      |    port: 18080,
      |    interface: "0.0.0.0"
      |}
    """.stripMargin

  sealed trait GuardianProtocol
  val guardian: Behavior[GuardianProtocol] = Behaviors.setup {
    ctx =>
      implicit val actorContext = ctx
      val config = ConfigSource.string(configString).loadOrThrow[GatewayConfig]
      GatewayServer(config)
      Behaviors.ignore
  }


  val dogsWireMockServer = new WireMockServer(wireMockConfig().port(9002))
  val vehiclesWireMockServer = new WireMockServer(wireMockConfig().port(9003))


  def initialiseMockServer(port: Int, path: String, mockServer: WireMockServer, delay: FiniteDuration) = {
    mockServer.start()

    val wireMock = new WireMock("localhost", port)
    wireMock.register(
      get(urlEqualTo(path))
        .willReturn(
          aResponse().withFixedDelay(delay.toMillis.intValue())
            .withStatus(200))
    )
    WireMock.configureFor("localhost", port)
  }


  val actorSystem = ActorSystem(guardian, "GuardianBulkheadSpec")

  override def beforeAll(): Unit = {
    initialiseMockServer(9002, "/dogs/", dogsWireMockServer, 5 seconds)
    initialiseMockServer(9003, "/bikes/", vehiclesWireMockServer, 10 milli)
  }

  override def afterAll(): Unit = {
    dogsWireMockServer.stop()
    vehiclesWireMockServer.stop()
    Kamon.stopAllReporters()
    actorSystem.terminate()
  }

  implicit val system = actorSystem.toClassic
  implicit val materializer = Materializer(system)
  implicit val executionContext = actorSystem.executionContext

  "overflowing one queue" must "not affect another" in {
    for (_ <- 1 to 16) {
        Http().singleRequest(
          HttpRequest(uri = Uri("http://localhost:18080/animals/dogs")),
          settings = ConnectionPoolSettings.default.withMaxConnections(32)
      ).map(_.discardEntityBytes())
    }

    val awaitableResult =
      Http().singleRequest(
        HttpRequest(uri = Uri("http://localhost:18080/vehicles/bikes")),
        settings = ConnectionPoolSettings.default.withMaxConnections(32)
      )

    val result = Await.result(awaitableResult, 2 seconds)
    result.discardEntityBytes()
    result.status shouldBe StatusCodes.OK
  }



}
