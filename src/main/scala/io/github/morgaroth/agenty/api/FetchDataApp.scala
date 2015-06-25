package io.github.morgaroth.agenty.api

import scala.util.Try

object FetchDataApp extends Backend with AgentsLogic {
  def main(args: Array[String]) {
    val result = fetchConvert(Try(args(0).toInt).toOption.getOrElse(5000), saveToDB = true)
    println(s"Fetched ${result._1.length}, saved ${result._2.getOrElse(-1)} reddits.")
    system.shutdown()
  }
}
