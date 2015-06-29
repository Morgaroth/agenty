package io.github.morgaroth.agenty.agents

import akka.actor.{Props, ActorRef, Actor, ActorLogging}
import io.github.morgaroth.agenty.agents.GroupAgent.{GetUsers, GroupUsers}
import io.github.morgaroth.agenty.agents.User.Ready
import io.github.morgaroth.agenty.models.Author

object GroupAgent {

  case object GetUsers

  case class GroupUsers(users: Set[Author])

  def props(users: Set[Author], mother: ActorRef) = Props(classOf[GroupAgent], users, mother)

}

class GroupAgent(users: Set[Author], mother: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = {
    case Ready =>
    // nope
    case GetUsers => sender() ! GroupUsers(users)
  }
}
