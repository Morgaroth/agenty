package io.github.morgaroth.agenty.api

import spray.json._
import us.bleibinha.spray.json.macros.lazyy.json



case class RedditEntry(kind: String, data: RedditData)

case class RedditData(
                       created_utc: Option[Double],
                       author: Option[String],
                       id: Option[String],
                       parent_id: Option[String],
                       score: Option[Int],
                       domain: Option[String],
                       title: Option[String],
                       over_18: Option[Boolean],
                       selftext: Option[String],
                       body: Option[String],
                       replies: Option[RedditResponse]
                       )

object RedditData extends DefaultJsonProtocol {

  private val format: JsonFormat[RedditData] = lazyFormat(jsonFormat(RedditData.apply,
    "created_utc", "author", "id", "parent_id", "score", "domain", "title", "over_18", "selftext", "body", "replies"
  ))

  object formatter extends RootJsonFormat[RedditData] {
    override def write(obj: RedditData): JsValue = format.write(obj)

    override def read(json: JsValue): RedditData = format.read(json.asJsObject.copy(fields = json.asJsObject.fields.filter {
      case ("replies", JsString("")) => false
      case _ => true
    }))
  }

  implicit lazy val jsonFormatCommentData: JsonFormat[RedditData] = formatter
}

object RedditEntry extends DefaultJsonProtocol {

  import RedditData._

  implicit lazy val jsonFormatComment: JsonFormat[RedditEntry] = lazyFormat(jsonFormat(RedditEntry.apply, "kind", "data"))
}

@json case class RedditResponseData(modhash: String, children: List[RedditEntry])

@json case class RedditResponse(kind: String, data: RedditResponseData)
