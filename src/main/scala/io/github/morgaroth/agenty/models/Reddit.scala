package io.github.morgaroth.agenty.models

import io.github.morgaroth.agenty.models.JsonJodaTimeProtocol._
import org.joda.time.DateTime
import spray.json._
import us.bleibinha.spray.json.macros.json

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
                    created: DateTime,
                    comments: List[Comment]) extends RedditBase {
  override def content: String = body
}


case class Author(id: String) {
  def normalized: String = id
    .replaceAll( """\[""", "left-square-bracket")
    .replaceAll( """\]""", "right-square-bracket")
}

object Author {
  implicit val jsonFormatAuthor = jsonFormat1(Author.apply)
}


object Comment extends DefaultJsonProtocol {

  import Author._

  implicit lazy val jsonFormat: JsonFormat[Comment] = lazyFormat(
    jsonFormat(Comment.apply, "body", "author", "commentee", "score", "created", "comments")
  )
}


@json case class Reddit(
                         title: String,
                         author: Author,
                         score: Int,
                         created: DateTime,
                         comments: List[Comment]
                         ) extends RedditBase {
  override def content: String = title
}

