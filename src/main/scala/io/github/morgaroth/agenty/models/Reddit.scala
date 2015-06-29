package io.github.morgaroth.agenty.models

import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import com.novus.salat.annotations.Key
import com.novus.salat.conversions.RegisterJodaTimeZoneConversionHelpers
import com.novus.salat.global.ctx
import com.typesafe.config.ConfigFactory
import io.github.morgaroth.agenty.models.JsonJodaTimeProtocol._
import io.github.morgaroth.utils.mongodb.salat.MongoDAOStringKey
import org.bson.types.ObjectId
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

case class RedditDB(
                     reddit: Reddit,
                     @Key("_id") id: String
                     )

object RedditDB {
  RegisterJodaTimeConversionHelpers()

  //  RegisterJodaTimeZoneConversionHelpers()

  def from(reddit: Reddit): RedditDB = RedditDB(reddit, s"${reddit.author.id}__${reddit.created.getMillis}")

  lazy val dao = new MongoDAOStringKey[RedditDB](ConfigFactory.load().getConfig("db"), "reddits") {}

}