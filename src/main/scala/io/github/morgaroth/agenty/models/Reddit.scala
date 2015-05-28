package io.github.morgaroth.agenty.models

import us.bleibinha.spray.json.macros.json
import spray.json._
import spray.json.DefaultJsonProtocol._

@json case class Author(id: String)


@json case class Reddit(
                         title: String,
                         author: Author,
                         score: Int
                         )
