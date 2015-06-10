package io.github.morgaroth.agenty.agents

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import io.github.morgaroth.agenty.models.Author
import akka.pattern.ask

import scala.collection.mutable

object Mother {
  def props: Props = Props(classOf[Mother])

  val name = "mother_of_all"

  case class ActorFor(name: Author)

  case class Broadcast(message: Any, timeout: Timeout)

  case class ActorOf(name: Author, ref: ActorRef)

}

class Mother extends Actor with ActorLogging {

  import Mother._

  val users = mutable.Map.empty[String, ActorRef]

  override def receive: Receive = {
    case ActorFor(author) =>
      val ref = users.getOrElseUpdate(author.normalized, context.actorOf(User.props(author, self), author.normalized))
      sender() ! ActorOf(author, ref)
    case Broadcast(msg, timeout) =>
      implicit val tm = timeout
      sender() ! context.children.map(ch => ch ? msg).toList
  }
}
