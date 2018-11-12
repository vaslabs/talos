package talos.gateway

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.model._
import talos.gateway.Gateway.ServiceCall

import scala.concurrent.Future
class GatewaySpec extends WordSpec with Matchers with ScalatestRouteTest{


  "talos gateway" can {

    val talosGateway = Gateway(TestUtils.gatewayConfiguration, (httpCommand: Gateway.HttpCall) =>
      httpCommand match {
        case ServiceCall(_, hit) =>
          Future.successful(HttpResponse(entity = s"${hit.service} ${hit.targetPath}"))
      }
    )

    "forward traffic to configured service" in {

      Get("/foo/") ~> talosGateway.route ~> check {
        responseAs[String] shouldBe "fooservice /"
      }
    }
    "forward traffic to different endpoints" in {
      Get("/foobar/") ~> talosGateway.route ~> check {
        responseAs[String] shouldBe "fooservice /bar"
      }
    }
  }

}
