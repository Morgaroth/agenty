package io.github.morgaroth.agenty.api

import akka.actor.ActorSystem
import akka.event.Logging
import io.github.morgaroth.agenty.models.{Author, Reddit}
import io.github.morgaroth.agenty.reddit.redditUrls
import spray.client.pipelining._
import spray.httpx.SprayJsonSupport
import spray.json._
import spray.routing._

import scala.concurrent.Future
import scala.util.{Success, Failure, Try}

trait Backend extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val system = ActorSystem("LOCAL")

  import system.dispatcher

  val redditsPIpe = sendReceive ~> unmarshal[RedditResponse]
  val redditDetails = sendReceive ~> unmarshal[List[RedditResponse]]
}

trait WebApi extends Directives with redditUrls with DefaultJsonProtocol with SprayJsonSupport {
  this: Backend =>

  import system.dispatcher

  val routes: Route =
    pathEndOrSingleSlash {
      get(complete("Hello"))
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
        pathEndOrSingleSlash {
          get(complete(fetchConvert))
        }
      }

  def fetch: Future[List[(RedditEntry, RedditResponse)]] = {
    val redditsEntries: Future[List[RedditEntry]] = redditsPIpe(Get(europe(1000))).map(_.data.children)
    val b: Future[List[(RedditEntry, RedditResponse)]] = redditsEntries.flatMap { reddits =>
      println(s"fetched reddits ${reddits.size}")
      val list: List[Future[Option[(RedditEntry, RedditResponse)]]] = reddits.map { reddit =>
        val commentUrl: String = comment(reddit.data.id.get)
        println(commentUrl)
        val a: Try[Future[Option[RedditResponse]]] = try {
          val map = redditDetails(Get(commentUrl)).map(_.find(_.data.children.exists(_.kind == "t1")))
          val b = map.recover {
            case t: Throwable =>
              println(s"$commentUrl fail with $t")
              None
          }
          b.foreach(_.foreach(println(_)))
          Success(b)
        } catch {
          case t: Throwable =>
            println(s"$commentUrl fail throwing with $t")
            Failure(t)
        }
        a.getOrElse(Future.successful(None)).map(_.map(x => reddit -> x))
      }
      Future.sequence(list).map(_.flatten.toList)
    }
    b
  }

  def fetchConvert: Future[List[Reddit]] = {
    val raw = fetch
    //    val authors: Future[List[String]] = raw.map(_.flatMap(_._2.data.children.map(_.data.author)).flatten)
    //    authors.flatMap( ids =>
    //
    //    )
    raw.map(_.map {
      case (red, commentRaw) =>
        for {
          author <- red.data.author
          points <- red.data.score
          title <- red.data.title
        } yield Reddit(title, Author(author), points)
    }).map(_.flatten)
  }
}