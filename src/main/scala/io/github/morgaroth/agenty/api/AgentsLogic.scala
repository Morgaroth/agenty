package io.github.morgaroth.agenty.api

import akka.pattern.ask
import akka.util.Timeout
import com.mongodb.casbah.commons.MongoDBObject
import io.github.morgaroth.agenty.agents.GroupAgent.{GetUsers, GroupUsers}
import io.github.morgaroth.agenty.agents.Mother._
import io.github.morgaroth.agenty.agents.User._
import io.github.morgaroth.agenty.models.{Author, Comment, Reddit, RedditDB}
import io.github.morgaroth.agenty.reddit.redditUrls
import org.joda.time.{DateTime, DateTimeZone}
import spray.client.pipelining._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
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

  def simulateSteps() = {

    implicit val tm: Timeout = 20 seconds

    val dataIt = RedditDB.dao.find(MongoDBObject.empty).sort(MongoDBObject("reddit.created" -> 1)).map(_.reddit)

    var currentGroups: Map[Set[Author], Int] = Map.empty

    val statistics = mutable.Map.empty[Int, Int]
    val redditStats = mutable.Map.empty[Int, Int]

    while (dataIt.hasNext) {
      val nextreddit = dataIt.next()
      mother ! Clear
      Thread.sleep(2000)
      val res: Future[Any] = (mother ? ActorFor(nextreddit.author)).mapTo[ActorOf].map(_.ref).flatMap(c => (c ? nextreddit).mapTo[Handled.type])
      Await.result(res, 30.seconds)
      println(s"reddit ${nextreddit.author.normalized} from ${nextreddit.created} handled")
      Await.result((mother ? Broadcast(FindGroups, tm)).mapTo[List[Ready.type]], 50 seconds)
      Thread.sleep(6000)
      val groups: List[Set[Author]] = Await.result(
        (mother ? BroadcastGroup(GetUsers, 240 seconds)).mapTo[List[Future[GroupUsers]]].flatMap(x => Future.sequence(x)),
        250 seconds
      ).map(_.users)
      println(s"life ${groups.size}")

      // update reddit stats
      redditStats.update(groups.size, redditStats.getOrElse(groups.size, 0) + 1)

      val (liveGroups, deadGroups) = currentGroups.partition(groups.contains)
      val newGroups = groups.filterNot(currentGroups.contains)
      // update statistics with dead groups
      deadGroups.foreach { x =>
        val nev = statistics.getOrElseUpdate(x._2, 0)
        statistics.update(x._2, nev + 1)
      }
      println(s"dead ${deadGroups.size}")

      // update life groups
      currentGroups = Map.empty
      currentGroups ++= liveGroups.map(x => x._1 -> (x._2 + 1))
      currentGroups ++= newGroups.map(x => x -> 1)
      println(statistics.mkString("groups life: [Days, Count]: ", ", ", ""))
      println(redditStats.mkString("groups in reddirs: [Groups, Count]: ", ", ", ""))
    }
    currentGroups.foreach { x =>
      val nev = statistics.getOrElse(x._2, 1)
      statistics.update(x._2, nev)
    }
    println(statistics)
    (statistics.toMap, redditStats.toMap)
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
      val saveResult = reddits.map(RedditDB.from).map(RedditDB.dao.save(_: RedditDB))
      println(s"saved ${saveResult.size}")
      Some(saveResult.length)
    } else None
    (reddits, save)
  }

  def calculateCommentsMean(): (Int, Int) = {
    def countComments(comments: List[Comment]): Int = {
      comments match {
        case Nil => 0
        case any => any.map(x => countComments(x.comments)).sum + any.length
      }
    }

    val dataIt = RedditDB.dao.find(MongoDBObject.empty).map(_.reddit)
    dataIt.foldLeft((0, 0)) {
      case ((comments, reddits), r) => (comments + countComments(r.comments), reddits + 1)
    }
  }
}
