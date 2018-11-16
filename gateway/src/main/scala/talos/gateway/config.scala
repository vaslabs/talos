package talos.gateway

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import pureconfig._
import pureconfig.error.{CannotConvert, ConfigReaderFailures}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

object config {

  case class GatewayConfig(services: List[ServiceConfig], interface: String, port: Int)

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
    callTimeout: FiniteDuration,
    importance: ServiceImportance
  )

  case class Mapping(
    gatewayPath: GatewayPath,
    methods: List[HttpMethod],
    targetPath: String
  )

  sealed trait ServiceImportance {
    def consecutiveFailuresThreshold: Int
    def resetTimeout: FiniteDuration
  }

  object High extends ServiceImportance {
    override val consecutiveFailuresThreshold = 50

    override val resetTimeout: FiniteDuration = 15 seconds
  }
  object Medium extends ServiceImportance {
    override val consecutiveFailuresThreshold = 10
    override val resetTimeout = 30 seconds
  }
  object Low extends ServiceImportance {
    override val consecutiveFailuresThreshold = 5
    override val resetTimeout = 60 seconds
  }

  case class GatewayPath(value: String) {
    require({
      val starIndex = value.lastIndexOf("/*")
      starIndex == -1 || starIndex == value.length - 2
    })
  }

  object GatewayPath {
    implicit def fromString(value: String): GatewayPath = GatewayPath(value)
  }

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

    import cats.syntax.either._

    implicit val gatewayPathReader: ConfigReader[GatewayPath] = ConfigReader[String].emap(
      path => Either.catchNonFatal(GatewayPath(path)).left.map(
        _ => CannotConvert(path, "GatewayPath", "/* are only allowed at the end")
      )
    )

    implicit val importanceReader: ConfigReader[ServiceImportance] = ConfigReader[String].emap(
      _ match {
        case "High" => Right(High)
        case "Low" => Right(Low)
        case "Medium" => Right(Medium)
        case unrecognised => Left(CannotConvert(unrecognised, "ServiceImportance", "Accepts only High/Medium/Low"))
      }
    )
  }

}
