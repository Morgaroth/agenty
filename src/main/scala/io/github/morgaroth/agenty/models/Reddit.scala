package io.github.morgaroth.agenty.models

import us.bleibinha.spray.json.macros.json
import spray.json._
import spray.json.DefaultJsonProtocol._

trait RedditBase {
  def content: String

  def author: Author

  def score: Int

  def comments: List[Comment]
}

case class Comment(
                    body: String,
                    author: Author,
                    commented: Author,
                    score: Int,
                    comments: List[Comment]) extends RedditBase {
  override def content: String = body
}


case class Author(id: String) {
  def normalized: String = id
}

object Author {
  implicit val jsonFormatAuthor = jsonFormat1(Author.apply)
}


object Comment extends DefaultJsonProtocol {

  import Author._

  implicit lazy val jsonFormat: JsonFormat[Comment] = lazyFormat(
    jsonFormat(Comment.apply, "body", "author", "commentee", "score", "comments")
  )
}


import Comment.jsonFormat


@json case class Reddit(
                         title: String,
                         author: Author,
                         score: Int,
                         comments: List[Comment]
                         ) extends RedditBase {
  override def content: String = title
}

