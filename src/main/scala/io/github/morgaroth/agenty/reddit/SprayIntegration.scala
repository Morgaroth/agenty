package io.github.morgaroth.agenty.reddit

import akka.actor.ActorSystem
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.client.pipelining._

trait SprayIntegration extends DefaultJsonProtocol with SprayJsonSupport {

  def name: String

}
