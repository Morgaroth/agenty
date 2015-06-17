package io.github.morgaroth.agenty.models

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import spray.json._

import scala.util.Try

/**
 * ISO Date serializer
 */
trait JsonJodaTimeProtocol extends DefaultJsonProtocol {

  implicit object DateTimeFormat extends RootJsonFormat[DateTime] {

    val parser = ISODateTimeFormat.dateOptionalTimeParser()

    def write(obj: DateTime): JsValue = {
      JsString(ISODateTimeFormat.basicDateTime.print(obj))
    }

    def read(json: JsValue): DateTime = json match {
      case JsString(s) => Try(parser.parseDateTime(s)).getOrElse(error(s))
      case _ => error(json.toString())
    }

    def error(v: Any): DateTime = {
      deserializationError(
        s"""
           |'$v' is not a valid date value. Dates must be in format:
                 |     * date-opt-time     = date-element ['T' [time-element] [offset]]
                 |     * date-element      = std-date-element | ord-date-element | week-date-element
                 |     * std-date-element  = yyyy ['-' MM ['-' dd]]
                 |     * ord-date-element  = yyyy ['-' DDD]
                 |     * week-date-element = xxxx '-W' ww ['-' e]
                 |     * time-element      = HH [minute-element] | [fraction]
                 |     * minute-element    = ':' mm [second-element] | [fraction]
                 |     * second-element    = ':' ss [fraction]
                 |     * offset            = 'Z' | (('+' | '-') HH [':' mm [':' ss [('.' | ',') SSS]]])
                 |     * fraction          = ('.' | ',') digit+
        """.stripMargin
      )
    }
  }

}

object JsonJodaTimeProtocol extends JsonJodaTimeProtocol