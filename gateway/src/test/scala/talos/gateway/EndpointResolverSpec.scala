package talos.gateway

import akka.http.scaladsl.model.{HttpMethods, StatusCodes}
import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest

class EndpointResolverSpec extends WordSpec with Matchers with ScalatestRouteTest{

  "endpoint resolver" can {

    "resolve http verbs to akka routes" in {
      EndpointResolver.resolve(HttpMethods.GET) shouldBe Right(get)
      EndpointResolver.resolve(HttpMethods.POST) shouldBe Right(post)
      EndpointResolver.resolve(HttpMethods.PUT) shouldBe Right(put)
      EndpointResolver.resolve(HttpMethods.DELETE) shouldBe Right(delete)
      EndpointResolver.resolve(HttpMethods.PATCH) shouldBe Right(patch)
    }

    "resolve simple paths to akka routes" in {
      val catsPath = EndpointResolver.resolve(HttpMethods.GET).map(
        getR =>
          EndpointResolver.resolve("/cats") {
            getR {
              complete(StatusCodes.OK)
            }
          }
      )

      catsPath should matchPattern {
        case Right(_) =>
      }

      catsPath.map {
        catsRoute: Route =>
          Get("/cats") ~> catsRoute ~> check {
            response.status shouldBe StatusCodes.OK
          }
      }
    }

  }

}
