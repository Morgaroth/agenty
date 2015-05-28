package io.github.morgaroth.agenty.reddit

object auth {

  val SCOPE_STRING = "identity"

  val DURATION = "permanent"

  val CLIENT_ID = "H97y2xREE5Us9w"

  val RANDOM_STRING = "random123"

  val URI = "http://127.0.0.1:30889/auth/reddit/"

  val TYPE = "code"

  val url = s"https://www.reddit.com/api/v1/authorize?client_id=$CLIENT_ID&response_type=$TYPE&state=$RANDOM_STRING&redirect_uri=$URI&duration=$DURATION&scope=$SCOPE_STRING"
}
