package io.github.morgaroth.agenty.reddit

trait redditUrls {

  val reddit = "http://www.reddit.com/r"

  def europe(limit: Int = 1000, after: Option[String]) = s"$reddit/europe.json?limit=$limit${after.map(x => s"&after=$x").getOrElse("")}"

  def comment(redditId: String) = s"$reddit/europe/comments/$redditId.json"
}