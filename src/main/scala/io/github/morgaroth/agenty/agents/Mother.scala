package io.github.morgaroth.agenty.agents

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import io.github.morgaroth.agenty.agents.User.{Ready, CleanFriends}
import io.github.morgaroth.agenty.models.Author
import akka.pattern.ask
import scala.collection.mutable
import scala.concurrent.Future

object Mother {
  def props: Props = Props(classOf[Mother])

  val name = "mother_of_all"

  case class GroupActorFor(users: Set[Author])

  case class ActorFor(name: Author)

  case class Broadcast(message: Any, timeout: Timeout)

  case class BroadcastGroup(message: Any, timeout: Timeout)

  case class ActorOf(name: Author, ref: ActorRef)

  case class GroupActorOf(name: Set[Author], ref: ActorRef)

  case object Clear

}

class Mother extends Actor with ActorLogging {

  import Mother._

  val users = mutable.Map.empty[String, ActorRef]
  val groups = mutable.Map.empty[Set[String], ActorRef]

  override def receive: Receive = {
    case ActorFor(author) =>
      val ref = users.getOrElseUpdate(author.normalized, context.actorOf(User.props(author, self), author.normalized))
      sender() ! ActorOf(author, ref)
    case GroupActorFor(authors) =>
      log.info(s"request for creating group actor for $authors")
      val authorsStr: Set[String] = authors.map(_.normalized)
      val ref = groups.getOrElseUpdate(authorsStr, context.actorOf(GroupAgent.props(authors, self), authorsStr.mkString("group___", "__", "")))
      sender() ! GroupActorOf(authors, ref)
    case Broadcast(msg, timeout) =>
      implicit val tm = timeout
      sender() ! users.values.map(ch => ch ? msg).toList
    case BroadcastGroup(msg, timeout) =>
      implicit val tm = timeout
      sender() ! groups.values.map(ch => ch ? msg).toList
    case Clear =>
      val requester = sender()
      users.values.foreach(ch => ch ! CleanFriends)
      groups.clear()
  }
}
