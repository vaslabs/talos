package talos.gateway

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._

object EndpointResolver {

  def resolve(httpMethod: HttpMethod): Either[String, Directive0] = httpMethod match {
    case HttpMethods.GET => Right(get)
    case HttpMethods.PATCH => Right(patch)
    case HttpMethods.PUT => Right(put)
    case HttpMethods.DELETE => Right(delete)
    case HttpMethods.POST => Right(post)
    case _ => Left("Unsupported http method")
  }

  def resolve(pathFragment: String): Directive0 = {
    val removeTrailingSlash =
      if (pathFragment.charAt(0) == '/')
        pathFragment.substring(1)
      else
        pathFragment
    path(separateOnSlashes(removeTrailingSlash))
  }
}
