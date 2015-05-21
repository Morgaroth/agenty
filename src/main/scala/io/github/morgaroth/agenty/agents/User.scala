package io.github.morgaroth.agenty.agents

import akka.actor.{Actor, ActorLogging}
import scalaz.Scalaz._

class User(id: UserID) extends Actor with ActorLogging {

  var friends = Map.empty[UserID, Friendship]
  var wordCount = Map.empty[String, Int]

  override def receive: Receive = {

    case Statement(thread, content) =>
      val wc: Map[String, Int] = content.split( """[,\. :\(\)\[\]\\]+""").groupBy(identity).mapValues(_.length)
      wordCount = wordCount |+| wc
    case unhandled =>
      log.warning(s"unhandled message $unhandled")
  }
}
