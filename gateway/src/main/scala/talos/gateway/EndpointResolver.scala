package talos.gateway

import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{HttpMethod, HttpMethods, HttpRequest}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive0, Directive1}
import cats.effect.IO
import talos.gateway.config.{Mapping, ServiceConfig}

object EndpointResolver {

  def transformRequest(request: HttpRequest, hitEndpoint: HitEndpoint): IO[HttpRequest] = IO {
    request.copy(uri = request.uri.withHost(hitEndpoint.service).withPath(Path(hitEndpoint.targetPath)).withPort(hitEndpoint.port))
  }


  case class HitEndpoint(service: String, port: Int, targetPath: String)

  def resolve(httpMethod: HttpMethod): Either[String, Directive0] = httpMethod match {
    case HttpMethods.GET => Right(get)
    case HttpMethods.PATCH => Right(patch)
    case HttpMethods.PUT => Right(put)
    case HttpMethods.DELETE => Right(delete)
    case HttpMethods.POST => Right(post)
    case _ => Left("Unsupported http method")
  }

  def resolve(pathFragment: String): Directive1[Option[String]] = {
    val removeTrailingSlash =
      if (pathFragment.charAt(0) == '/')
        pathFragment.substring(1)
      else
        pathFragment
    if (pathFragment.endsWith("/*")) {
      val withoutStar = removeTrailingSlash.substring(0, removeTrailingSlash.length - 2)
      pathPrefix(separateOnSlashes(withoutStar) / Remaining).map(remainingPath => Option(remainingPath))
    }
    else
      path(separateOnSlashes(removeTrailingSlash)).tmap(_ => Option.empty[String])
  }

  private[gateway] def mergeEitherDirectives[F](
        routeA: Either[String, Directive[F]], routeB: Either[String, Directive[F]]
  ): Either[String, Directive[F]] = for {
      methodFragmentA <- routeA
      methodFragmentB <- routeB
    } yield methodFragmentA | methodFragmentB


  def resolve(serviceConfig: ServiceConfig): Either[String, Directive1[HitEndpoint]] = {
    serviceConfig.mappings.map {
      case Mapping(gatewayPath, methods, targetPath) =>
        val methodsRoute: List[Either[String, Directive0]] = methods.map(resolve(_))

        val httpMethodRouteAggregation = methodsRoute.reduce(mergeEitherDirectives(_, _))

        val pathDirective: Directive1[Option[String]] = resolve(gatewayPath.value)
        httpMethodRouteAggregation.map(_ & pathDirective).map {
          _.map {
            remainingPathOpt =>
              val pathRemained = remainingPathOpt.getOrElse("")
              HitEndpoint(serviceConfig.host, serviceConfig.port, s"$targetPath$pathRemained")
          }
        }

    }.reduce((a, b) => mergeEitherDirectives(a, b))
  }
}
