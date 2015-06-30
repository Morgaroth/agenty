package io.github.morgaroth.agenty.api

import scala.util.Try

object Command extends Backend with AgentsLogic {
  def main(args: Array[String]) {
    args.toList match {
      case "fetch" :: redditsCount :: Nil =>
        val result = fetchConvert(Try(args(1).toInt).toOption.getOrElse(5000), saveToDB = true)
        println(s"Fetched ${result._1.length}, saved ${result._2.getOrElse(-1)} reddits.")
        system.shutdown()
      case "simulate" :: Nil =>
        val (stats, reddStats) = simulateSteps()
        println(stats.mkString("group statistics, how long they live in reddit units:\nreddits time live, count of occur\n", "\n", ""))
        println(stats.mkString("reddit statistics, how much groups was in one reddit:\ngroups from reddit, count of occur\n", "\n", ""))
        system.shutdown()
      case "comments_average" :: Nil =>
        val mean: (Int, Int) = calculateCommentsMean()
        println("result is:")
        println("comments count, reddits count, average comments per redit")
        println(s"${mean._1}, ${mean._2}, ${mean._1 * 1.0 / mean._2}")
        system.shutdown()
      case any =>
        println("no command, available: fetch $COUNT, simulate, comments_average.")
    }
  }
}
