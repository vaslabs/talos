package talos.gateway

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, Uri}
import akka.testkit.TestKit
import cats.effect.IO
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import talos.gateway.EndpointResolver.HitEndpoint
import talos.gateway.Gateway.ServiceCall
import talos.gateway.config.GatewayConfig
import cats.implicits._
class ExecutionApiSpec extends
    TestKit(ActorSystem("HttpExecutionApiSpec")) with WordSpecLike with BeforeAndAfterAll with Matchers{

  override def afterAll() {
    (IO.fromFuture(IO(system.terminate())) *> IO.unit).unsafeRunSync()
  }

  val config: GatewayConfig = TestUtils.gatewayConfiguration()

  "http execution api" can {
    val executionApi = ExecutionApi.apply(
      config,
      serviceCall => IO(HttpResponse(entity = s"${serviceCall.request.uri}"))
    )

    "forward http request to backend service" in {
      IO.fromFuture( IO {
          executionApi.executeCall(ServiceCall(HitEndpoint("fooservice", 8080, "/"), HttpRequest(uri = Uri("/foo"))))
        }
      ).unsafeRunSync().entity shouldBe HttpEntity("//fooservice:8080/")
    }
  }

}
