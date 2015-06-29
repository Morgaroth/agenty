package io.github.morgaroth.agenty.api

import akka.actor.ActorSystem
import io.github.morgaroth.agenty.agents.Mother
import io.github.morgaroth.agenty.reddit.redditUrls
import spray.client.pipelining._
import spray.httpx.SprayJsonSupport
import spray.json._
import spray.routing._
import us.bleibinha.spray.json.macros.lazyy.json

import scala.language.postfixOps

@json case class Relation(source: String, target: String, weight: Int)

trait Backend extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val system = ActorSystem("LOCAL")

  import system.dispatcher

  val redditsPipe = sendReceive ~> unmarshal[RedditResponse]
  val redditDetails = sendReceive ~> unmarshal[List[RedditResponse]]

  val mother = system.actorOf(Mother.props, Mother.name)
}

trait WebApi extends Directives with redditUrls with DefaultJsonProtocol with SprayJsonSupport with AgentsLogic {
  this: Backend =>

  //@formatter:off
  import Relation._
  import system.dispatcher

  val routes: Route =
    pathEndOrSingleSlash {
      getFromResource("index.html")
    } ~
    pathPrefix("auth" / "reddit") {
      pathEndOrSingleSlash {
        get {
          parameterMap { par =>
            complete("Hello from $name$ application\n" + par.mkString(", ") + "end parameters")
          }
        }
      }
    } ~
    pathPrefix("fetch") {
      (pathEndOrSingleSlash & parameters('reddits ? 100, 'saveToDb ? false)) { (cnt, save) =>
        get(complete(fetchConvert(cnt, save)))
      }
    } ~
    pathPrefix("go") {
      pathEndOrSingleSlash {
        parameters('reddits ? 100) { reddits =>
          get(complete(simulate(reddits)))
        }
      }
    } ~
    pathPrefix("simulate") {
      pathEndOrSingleSlash {
        get(complete(simulateSteps()))
      }
    } ~
    pathPrefix("data" / "users") {
      pathEndOrSingleSlash {
        get(complete(getFriends))
      }
    }
  //@formatter:on
}