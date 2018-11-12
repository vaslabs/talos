package talos.gateway

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import pureconfig._
import pureconfig.error.{CannotConvert, ConfigReaderFailures}

import scala.concurrent.duration.FiniteDuration

object config {

  case class GatewayConfig(services: List[ServiceConfig])

  object GatewayConfig {

    def load: Either[ConfigReaderFailures, GatewayConfig] = {
      import pureconfigExt._
      import pureconfig.generic.auto._
      loadConfig[GatewayConfig]("talos.gateway")
    }
  }

  case class ServiceConfig(
    secure: Boolean,
    host: String,
    port: Int,
    mappings: List[Mapping],
    maxInflightRequests: Int,
    callTimeout: FiniteDuration
  )

  case class Mapping(
    gatewayPath: String,
    methods: List[HttpMethod],
    targetPath: String
  )

  private[gateway] object pureconfigExt {
    implicit val httpMethodReader: ConfigReader[HttpMethod] = ConfigReader[String].emap {
      _ match {
        case "POST" => Right(HttpMethods.POST)
        case "GET" => Right(HttpMethods.GET)
        case "PUT" => Right(HttpMethods.PUT)
        case "DELETE" => Right(HttpMethods.DELETE)
        case "PATCH" => Right(HttpMethods.PATCH)
        case other =>
          Left(
            CannotConvert(
              other,
              "HttpMethod",
              "Unrecognised/Unsupported http verb. Supported methods: POST, GET, PUT, DELETE, PATCH"
            )
          )
      }
    }
  }
}
