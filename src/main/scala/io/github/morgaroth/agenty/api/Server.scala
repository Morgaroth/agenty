package io.github.morgaroth.agenty.api

import akka.actor._
import akka.io.IO
import com.typesafe.config.ConfigFactory
import spray.can.Http
import spray.routing._

trait serviceActor {
  this: WebApi =>
  lazy val serviceActorProps = Props(new HttpServiceActor {
    override def receive: Actor.Receive = runRoute(routes)
  })
}

object Server extends App with Backend with WebApi with serviceActor {

  val rootService = system.actorOf(serviceActorProps)

  val port = ConfigFactory.load().getInt("http.port")

  IO(Http)(system) ! Http.Bind(rootService, "0.0.0.0", port = port)
}