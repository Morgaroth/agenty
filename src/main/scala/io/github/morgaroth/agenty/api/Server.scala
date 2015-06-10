package io.github.morgaroth.agenty.api

import akka.actor._
import akka.io.IO
import com.typesafe.config.ConfigFactory
import spray.can.Http
import spray.routing._


import spray.http.HttpHeaders._
import spray.http.HttpMethods._
import spray.http.{AllOrigins, HttpMethod, HttpMethods, HttpResponse}
import spray.routing._

trait CORSSupport {
  this: HttpService =>

  def AllowOriginHeader = AllOrigins

  val BasicAllowedHeaders: List[String] = List(
    Origin.name, `Content-Type`.name, Accept.name,
    `Accept-Encoding`.name, `Accept-Language`.name,
    Host.name, `User-Agent`.name, "X-Requested-With",
    "Referer"
  )

  /**
   * Override when need to add more allowed headers
   * @return
   */
  def AdditionalAllowedHeaders: List[String] = List.empty

  val basicCORSHeaders = List(
    `Access-Control-Allow-Headers`(BasicAllowedHeaders ::: AdditionalAllowedHeaders),
    `Access-Control-Allow-Origin`(AllowOriginHeader),
    `Access-Control-Max-Age`(1728000)
  )

  def cors[T]: Directive0 = mapRequestContext {
    ctx => ctx.withRouteResponseHandling {
      //It is an option request for a resource that responds to some other method
      case Rejected(x) if ctx.request.method.equals(HttpMethods.OPTIONS) && x.exists(_.isInstanceOf[MethodRejection]) =>
        val allowedMethods: List[HttpMethod] = x.filter(_.isInstanceOf[MethodRejection]).map(rejection => {
          rejection.asInstanceOf[MethodRejection].supported
        })
        ctx.complete(HttpResponse().withHeaders(
          `Access-Control-Allow-Methods`(OPTIONS, allowedMethods: _*) :: basicCORSHeaders
        ))
    }.withHttpResponseHeadersMapped(headers => `Access-Control-Allow-Origin`(AllowOriginHeader) :: headers)
  }
}

trait serviceActor {
  this: WebApi =>
  lazy val serviceActorProps = Props(new HttpServiceActor with CORSSupport{
    override def receive: Actor.Receive = runRoute(cors(routes))
  })
}

object Server extends App with Backend with WebApi with serviceActor {

  val rootService = system.actorOf(serviceActorProps)

  val port = ConfigFactory.load().getInt("http.port")

  IO(Http)(system) ! Http.Bind(rootService, "0.0.0.0", port = port)
}