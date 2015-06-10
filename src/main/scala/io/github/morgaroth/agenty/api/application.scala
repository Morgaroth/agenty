package io.github.morgaroth.agenty.api

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import io.github.morgaroth.agenty.agents.Mother
import io.github.morgaroth.agenty.agents.Mother.{ActorFor, ActorOf}
import io.github.morgaroth.agenty.models.{Author, Comment, Reddit}
import io.github.morgaroth.agenty.reddit.redditUrls
import spray.client.pipelining._
import spray.httpx.SprayJsonSupport
import spray.json._
import spray.routing._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

trait Backend extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val system = ActorSystem("LOCAL")

  import system.dispatcher

  val redditsPipe = sendReceive ~> unmarshal[RedditResponse]
  val redditDetails = sendReceive ~> unmarshal[List[RedditResponse]]

  val mother = system.actorOf(Mother.props, Mother.name)
}

trait WebApi extends Directives with redditUrls with DefaultJsonProtocol with SprayJsonSupport {
  this: Backend =>

  import system.dispatcher

  //@formatter:off
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
    } ~
    pathPrefix("go") {
      pathEndOrSingleSlash {
        get(complete(simulate))
      }
    }
  //@formatter:on


  def simulate: Future[List[String]] = {
    import system.dispatcher
    implicit val tm: Timeout = 20 seconds
    val data: Future[List[Reddit]] = fetchConvert

    data.map(_.map { reddit =>
      (mother ? ActorFor(reddit.author)).mapTo[ActorOf].map(_.ref).foreach(_ ! reddit)
      reddit.author.normalized
    })
  }

  def fetch: Future[List[(RedditEntry, RedditResponse)]] = {
    val redditsEntries: Future[List[RedditEntry]] = redditsPipe(Get(europe(50))).map(_.data.children)
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
          b.foreach(_.foreach(x => println(x.toString take 100)))
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

  def parseComments(in: Option[RedditResponse], parent: Author): List[Comment] = {
    println(in.toString.take(30))
    in.map(input =>
      for {
        entry <- input.data.children
        unpacked = entry.data
        author <- unpacked.author
        authorObj = Author(author)
        score <- unpacked.score
        body <- unpacked.body
      } yield Comment(body, authorObj, parent, score, parseComments(unpacked.replies, authorObj))
    ).getOrElse(List.empty)
  }


  def fetchConvert: Future[List[Reddit]] = {
    val raw = fetch
    raw.map(_.map {
      case (red, commentRaw) =>
        for {
          author <- red.data.author
          authorObj = Author(author)
          points <- red.data.score
          title <- red.data.title
          comments = parseComments(Some(commentRaw), authorObj)
        } yield Reddit(title, authorObj, points, comments)
    }).map(_.flatten)
  }
}