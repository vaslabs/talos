package talos.gateway

import akka.http.scaladsl.model.HttpMethods
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}
import pureconfig._
import pureconfig.error.{CannotConvert, ConfigReaderFailures, ConvertFailure}
import talos.gateway.config._

import scala.concurrent.duration._

import config.pureconfigExt._
import pureconfig.generic.auto._
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
                List(HttpMethods.GET, HttpMethods.POST),
                "/animals/dogs/"
              ),
              Mapping(
                "/cats",
                List(HttpMethods.PUT, HttpMethods.DELETE),
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
                List(HttpMethods.PATCH),
                "/vehicles/cars/"
              ),
              Mapping(
                "/bikes",
                List(HttpMethods.GET, HttpMethods.DELETE),
                "/vehicles/bikes/"
              )
            ),
            4,
            15 seconds
          )
        ),
        "0.0.0.0",
        8080
      )
    )
  }

  it must "not accept invalid http verbs" in {
    val configString =
      """
      {
        gateway-path = "s",
        methods: [PUT, WHAT],
        target-path = "b"
      }
      """.stripMargin

    loadConfig[Mapping](ConfigFactory.parseString(configString)) should matchPattern {
      case Left(ConfigReaderFailures(ConvertFailure(CannotConvert("WHAT", "HttpMethod", _), _, _), _)) =>
    }
  }

  it must "accept wildcard * only at the end of the path" in {
    val configString =
      """
      {
        gateway-path = "s/*/a",
        methods: [PUT],
        target-path = "b"
      }
      """.stripMargin

    loadConfig[Mapping](ConfigFactory.parseString(configString)) should matchPattern {
      case Left(ConfigReaderFailures(ConvertFailure(CannotConvert("s/*/a", "GatewayPath", _), _, _), _)) =>
    }

    val validConfigString =
      """
      {
        gateway-path = "s/*",
        methods: [PUT],
        target-path = "b"
      }
      """.stripMargin

    loadConfig[Mapping](ConfigFactory.parseString(validConfigString)) should matchPattern {
      case Right(_) =>
    }
  }

}
