package talos.gateway

import akka.http.scaladsl.model.HttpMethods
import org.scalatest.{FlatSpec, Matchers}
import pureconfig._
import talos.gateway.config._
import scala.concurrent.duration._


class ConfigSpec extends FlatSpec with Matchers {

  "basic configuration with multiple services" should "be deserialised" in {

    GatewayConfig.load shouldBe Right(
      GatewayConfig(
        List(
          ServiceConfig(
            false,
            "localhost",
            9000,
            List(
              Mapping(
                "/dogs",
                HttpMethods.GET,
                "/animals/dogs/"
              ),
              Mapping(
                "/cats",
                HttpMethods.POST,
                "/animals/cats/"
              )
            ),
            8,
            5 seconds
          ),
          ServiceConfig(
            true,
            "localhost",
            9001,
            List(
              Mapping(
                "/cars",
                HttpMethods.PUT,
                "/vehicles/cars/"
              ),
              Mapping(
                "/bikes",
                HttpMethods.DELETE,
                "/vehicles/bikes/"
              )
            ),
            4,
            15 seconds
          )
        )
      )
    )
  }

}
