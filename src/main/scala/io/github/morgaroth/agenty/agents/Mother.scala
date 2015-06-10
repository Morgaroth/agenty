package io.github.morgaroth.agenty.agents

import akka.actor.{Props, Actor, ActorLogging, ActorRef}
import io.github.morgaroth.agenty.models.Author

import scala.collection.mutable

object Mother {
  def props: Props = Props(classOf[Mother])

  val name = "mother_of_all"

  case class ActorFor(name: Author)

  case class ActorOf(name: Author, ref: ActorRef)

}

class Mother extends Actor with ActorLogging {

  import Mother._

  val users = mutable.Map.empty[String, ActorRef]

  override def receive: Receive = {
    case ActorFor(author) =>
      val ref = users.getOrElseUpdate(author.normalized, context.actorOf(User.props(author, self), author.normalized))
      sender() ! ActorOf(author, ref)
  }
}
