package io.github.morgaroth.agenty.agents

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import io.github.morgaroth.agenty.agents.Mother.{ActorFor, ActorOf}
import io.github.morgaroth.agenty.models.{Author, Comment, Reddit}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object User {
  def props(author: Author, mother: ActorRef) = Props(classOf[User], author, mother)

  case class Friendship(ref: ActorRef, value: Int)

  case object GetFriends

  case class MyFriends(me: Author, friends: Map[Author, Int])

}

class User(author: Author, mother: ActorRef) extends Actor with ActorLogging {

  import User._
  import context.dispatcher

  implicit val tm: Timeout = 4 seconds

  val friends = scala.collection.mutable.Map.empty[Author, Future[Friendship]]

  def notifyCommenters(comments: List[Comment]): Unit = {
    comments.foreach { comment =>
      getUser(comment.author).foreach(_ ! comment)
    }
  }

  override def receive: Receive = {
    case Reddit(_, _, _, comments) =>
      log.info(s"$author handle own reddit")
      notifyCommenters(comments)

    case comm: Comment =>
      log.info(s"$author handle own comment")
      friends.update(comm.commented, getFriend(comm.commented).map(f => f.copy(value = f.value + 1)))
      notifyCommenters(comm.comments)

    case GetFriends =>
      sender() ! MyFriends(author,
        (for {
          friendTuple <- friends
          (friend, friendship) = friendTuple if friendship.isCompleted
          friendshipSuccMay = friendship.value if friendship.value.isDefined
          friendshipSucc = friendship.value.get if friendship.value.get.isSuccess
          fr = friendshipSucc.get
        } yield friend -> fr.value
          ).toMap
      )


    case unhandled =>
      log.warning(s"unhandled message $unhandled in $author")
  }

  def getUser(author: Author): Future[ActorRef] = {
    log.debug(s"looking for an $author")
    friends
      .get(author).map(_.map(_.ref))
      .getOrElse((mother ? ActorFor(author)).mapTo[ActorOf].map(_.ref))
  }

  def getFriend(author: Author) = {
    log.debug(s"looking for an friend: $author")
    friends.getOrElseUpdate(
      author,
      (mother ? ActorFor(author)).mapTo[ActorOf].map { resp =>
        Friendship(resp.ref, 0)
      }
    )
  }
}
