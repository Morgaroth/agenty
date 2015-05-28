package io.github.morgaroth.agenty.models

import us.bleibinha.spray.json.macros.json
import spray.json._
import spray.json.DefaultJsonProtocol._


case class Comment(body: String, author: Author, score: Int, comments: List[Comment])


case class Author(id: String)
object Author{
  implicit val jsonFormatAuthor = jsonFormat1(Author.apply)
}


object Comment extends DefaultJsonProtocol {
  import Author._
  implicit lazy val jsonFormatra:JsonFormat[Comment] = lazyFormat(jsonFormat(Comment.apply, "body", "author", "score", "comments"))
}


import Comment.jsonFormatra


@json case class Reddit(
                         title: String,
                         author: Author,
                         score: Int,
                         comments: List[Comment]
                         )

