package talos.gateway

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes, Uri}
import cats.effect.IO
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import talos.gateway.EndpointResolver.HitEndpoint
import talos.gateway.Gateway.ServiceCall
import talos.gateway.config.GatewayConfig

import scala.concurrent.ExecutionContext
class ExecutionApiSpec extends WordSpec with BeforeAndAfterAll with Matchers{

  val testKit = ActorTestKit()
  implicit val system = testKit.system
  implicit val contextShift = IO.contextShift(ExecutionContext.global)


  override def afterAll() = testKit.shutdownTestKit()

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


    "escalate errors" in {
      val executionApi = ExecutionApi.apply(
        config,
        _ => IO(HttpResponse(status = StatusCodes.NotFound))
      )
      IO.fromFuture( IO {
        executionApi.executeCall(ServiceCall(HitEndpoint("fooservice", 8080, "/"), HttpRequest(uri = Uri("/foo"))))
      }
      ).unsafeRunSync().status shouldBe StatusCodes.NotFound

    }
  }

}
