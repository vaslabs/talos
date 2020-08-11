package talos.gateway

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import talos.gateway.Gateway.ServiceCall

import scala.concurrent.Future
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
class GatewaySpec extends AnyWordSpec with Matchers with ScalatestRouteTest{


  "talos gateway" can {

    val talosGateway = Gateway(TestUtils.gatewayConfiguration(), (httpCommand: Gateway.HttpCall) =>
      httpCommand match {
        case ServiceCall(hit, request) =>
          Future.successful(HttpResponse(entity = s"${hit.service} ${hit.targetPath} ${request.method.value}"))
      }
    )

    "forward traffic to configured service" in {

      Get("/foo/") ~> talosGateway.route ~> check {
        responseAs[String] shouldBe "fooservice / GET"
      }
    }

    "forward traffic to different endpoints" in {
      Get("/foobar/") ~> talosGateway.route ~> check {
        responseAs[String] shouldBe "fooservice /bar GET"
      }
    }

    "forward traffic for different http methods" in {
      Post("/foo/") ~> talosGateway.route ~> check {
        responseAs[String] shouldBe "fooservice / POST"
      }
    }

    "forward traffic to multiple services" in {
      Get("/bar/") ~> talosGateway.route ~> check {
        responseAs[String] shouldBe "barservice / GET"
      }
    }
  }

}
