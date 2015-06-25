package io.github.morgaroth.agenty.api

import akka.pattern.ask
import akka.util.Timeout
import com.mongodb.casbah.commons.MongoDBObject
import io.github.morgaroth.agenty.agents.Mother.{ActorFor, ActorOf, Broadcast}
import io.github.morgaroth.agenty.agents.User.{GetFriends, MyFriends}
import io.github.morgaroth.agenty.models.{Author, Comment, Reddit, RedditDB}
import io.github.morgaroth.agenty.reddit.redditUrls
import org.joda.time.{DateTime, DateTimeZone}
import spray.client.pipelining._

import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

trait AgentsLogic extends redditUrls {
  this: Backend =>

  import system.dispatcher

  def getFriends: Future[List[Relation]] = {
    implicit val tm: Timeout = 20 seconds

    (mother ? Broadcast(GetFriends, tm)).mapTo[List[Future[MyFriends]]].map(Future.sequence(_)).flatMap(identity).map(_.flatMap {
      (a: MyFriends) => a.friends.map(f => Relation(a.me.id, f._1.id, f._2))
    })
  }

  def simulate(cnt: Int): List[String] = {

    implicit val tm: Timeout = 20 seconds
    val data = fetchConvert(cnt)._1

    data.map { reddit =>
      (mother ? ActorFor(reddit.author)).mapTo[ActorOf].map(_.ref).foreach(_ ! reddit)
      reddit.author.normalized
    }
  }

  def fetch(cnt: Int): List[(RedditEntry, RedditResponse)] = {
    @tailrec def doStep(last: Option[String], ready: List[(RedditEntry, RedditResponse)]): List[(RedditEntry, RedditResponse)] = {
      if (ready.length >= cnt) return ready.take(cnt)
      val redditsEntries = redditsPipe(Get(europe(100, last)))
      val nextF = redditsEntries.map(_.data.after)
      val thisResponse = redditsEntries.map(_.data.children)
      val b: Future[List[(RedditEntry, RedditResponse)]] = thisResponse.flatMap { reddits =>
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
        Future.sequence(list).map(x => x.flatten)
      }
      val result = Await.result(b, 10.minutes)
      val next = Await.result(nextF, 10.seconds)
      doStep(next, ready ::: result)
    }
    doStep(None, List.empty)
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

  def fetchConvert(cnt: Int = 100, saveToDB: Boolean = false): (List[Reddit], Option[Int]) = {
    val raw = fetch(cnt)
    val reddits = raw.flatMap {
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
    }
    val save = if (saveToDB) {
      val existing = RedditDB.dao.find(MongoDBObject.empty).map(x => x.reddit.created.getMillis).toSet
      val nev = reddits.filterNot(r => existing.contains(r.created.getMillis)).map(RedditDB(_: Reddit))
      val saveResult = RedditDB.dao.insert(nev)
      println(s"saved ${saveResult.size}")
      Some(saveResult.length)
    } else None
    (reddits, save)
  }
}
