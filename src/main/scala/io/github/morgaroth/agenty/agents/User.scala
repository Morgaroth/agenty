package io.github.morgaroth.agenty.agents

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import io.github.morgaroth.agenty.agents.Mother.{GroupActorOf, GroupActorFor, ActorFor, ActorOf}
import io.github.morgaroth.agenty.models.{Author, Comment, Reddit}
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object User {
  def props(author: Author, mother: ActorRef) = Props(classOf[User], author, mother)

  case class Friendship(ref: ActorRef, value: Int)

  case object GetFriends

  case object EnoughFriends

  case object Handled

  case object Ready

  case object CleanFriends

  case object FindGroups

  case class MyFriends(me: Author, friends: Map[Author, Int])

  case class LookMyFriends(me: Author, friends: Map[Author, Int])

}

class User(author: Author, mother: ActorRef) extends Actor with ActorLogging {

  import User._
  import context.dispatcher

  implicit val tm: Timeout = 10 seconds

  val friends = mutable.Map.empty[Author, Future[Friendship]]


  var fullFriends = mutable.Map.empty[Author, Set[Author]]
  var maybeFullFriends: Set[Author] = _
  var friendsEnough = true


  def notifyCommenters(comments: List[Comment], requester: ActorRef) = {
    val list: List[Future[Handled.type]] = comments.map { comment =>
      getUser(comment.author).flatMap(c => (c ? comment).mapTo[Handled.type])
    }
    if (list.isEmpty) {
      requester ! Handled
    } else {
      Future.sequence(list).onSuccess {
        case anyth => requester ! Handled
      }
    }
  }

  override def receive: Receive = {
    case Reddit(_, _, _, _, comments) =>
//      log.info(s"$author handle own reddit")
      notifyCommenters(comments, sender())

    case comm: Comment =>
//      log.info(s"$author handle own comment")
      friends.update(comm.commented, getFriend(comm.commented).map(f => f.copy(value = f.value + 1)))
      notifyCommenters(comm.comments, sender())

    case GetFriends =>
      sender() ! MyFriends(author, myFriends)

    case FindGroups =>
      fullFriends = mutable.Map.empty[Author, Set[Author]]
      val friendsTMP: Map[Author, Int] = myFriends
      maybeFullFriends = friendsTMP.keySet
      friendsEnough = false
      context.system.scheduler.scheduleOnce(1 seconds) {
        friends.values.map(_.map(_.ref)).foreach(_.foreach(_ ! LookMyFriends(author, friendsTMP)))
        context.system.scheduler.scheduleOnce(2 seconds, self, EnoughFriends)
      }
      sender() ! Ready

    case LookMyFriends(friend, hisFriends) if maybeFullFriends.contains(friend) && !friendsEnough =>
      fullFriends += friend -> hisFriends.keySet

    case LookMyFriends(notMyFriend, _) if !friendsEnough =>
    // nope

    case CleanFriends =>
      friends.clear()

    case EnoughFriends =>
      friendsEnough = true
//      log.info(s"has ${fullFriends.size} good friends")
      val cliquesx3 = for {
        (myFriend, myFriendFriends) <- fullFriends
        (myAnoFri, myAnoFriendFriends) <- fullFriends - myFriend if myAnoFriendFriends.contains(myFriend) && myFriendFriends.contains(myAnoFri)
      } yield Set(myFriend, myAnoFri, author)

      cliquesx3.map(GroupActorFor).map(c => (mother ? c).mapTo[GroupActorOf]).foreach(_.foreach(_.ref ! Ready))

    case unhandled =>
      log.warning(s"unhandled message $unhandled of type ${unhandled.getClass.getCanonicalName} in $author")
  }

  def myFriends: Map[Author, Int] = {
    (for {
      friendTuple <- friends
      (friend, friendship) = friendTuple if friendship.isCompleted
      friendshipSuccMay = friendship.value if friendship.value.isDefined
      friendshipSucc = friendship.value.get if friendship.value.get.isSuccess
      fr = friendshipSucc.get
    } yield friend -> fr.value
      ).toMap
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
