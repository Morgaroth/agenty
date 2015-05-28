package io.github.morgaroth.agenty.reddit

trait redditUrls {

  val reddit = "http://www.reddit.com/r"

  def europe(limit: Int = 1000) = s"$reddit/europe.json?sort=hot&limit=$limit"

  def comment(redditId:String) = s"$reddit/europe/comments/$redditId.json"
}