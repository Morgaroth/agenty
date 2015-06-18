package io.github.morgaroth.agenty.api

import akka.pattern.ask
import akka.util.Timeout
import com.mongodb.casbah.MongoDB
import com.mongodb.casbah.commons.MongoDBObject
import io.github.morgaroth.agenty.agents.Mother.{ActorFor, ActorOf, Broadcast}
import io.github.morgaroth.agenty.agents.User.{GetFriends, MyFriends}
import io.github.morgaroth.agenty.models.{RedditDB, Author, Comment, Reddit}
import io.github.morgaroth.agenty.reddit.redditUrls
import org.joda.time.{DateTime, DateTimeZone}
import spray.client.pipelining._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

trait AgentsLogic {
  this: redditUrls with Backend =>

  import system.dispatcher

  def getFriends: Future[List[Relation]] = {
    implicit val tm: Timeout = 20 seconds

    (mother ? Broadcast(GetFriends, tm)).mapTo[List[Future[MyFriends]]].map(Future.sequence(_)).flatMap(identity).map(_.flatMap {
      (a: MyFriends) => a.friends.map(f => Relation(a.me.id, f._1.id, f._2))
    })
  }

  def simulate(cnt: Int): Future[List[String]] = {

    implicit val tm: Timeout = 20 seconds
    val data: Future[List[Reddit]] = fetchConvert(cnt)

    data.map(_.map { reddit =>
      (mother ? ActorFor(reddit.author)).mapTo[ActorOf].map(_.ref).foreach(_ ! reddit)
      reddit.author.normalized
    })
  }

  def fetch(cnt: Int): Future[List[(RedditEntry, RedditResponse)]] = {
    val redditsEntries: Future[List[RedditEntry]] = redditsPipe(Get(europe(cnt))).map(_.data.children)
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
        created <- unpacked.created_utc
        createdObj = DateTime.now(DateTimeZone.UTC).withMillis(created * 1000 toLong)
      } yield Comment(body, authorObj, parent, score, createdObj, parseComments(unpacked.replies, authorObj))
    ).getOrElse(List.empty)
  }

  def fetchConvert(cnt: Int = 100, saveToDB: Boolean = false): Future[List[Reddit]] = {
    val raw = fetch(cnt)
    val result = raw.map(_.map {
      case (red, commentRaw) =>
        for {
          author <- red.data.author
          authorObj = Author(author)
          points <- red.data.score
          title <- red.data.title
          created <- red.data.created_utc
          createdObj = DateTime.now(DateTimeZone.UTC).withMillis(created * 1000 toLong)
          comments = parseComments(Some(commentRaw), authorObj)
        } yield Reddit(title, authorObj, points, createdObj, comments)
    }).map(_.flatten)
    if (saveToDB) {
      result.foreach { reddits =>
        val existing = RedditDB.dao.find(MongoDBObject.empty).map(x => x.reddit.created).toSet
        val nev = reddits.filterNot(r => existing.contains(r.created)).map(RedditDB(_: Reddit))
        val saveResult = RedditDB.dao.insert(nev)
        println(saveResult)
      }
    }
    result
  }
}
