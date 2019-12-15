package org.voronar.telecode
import sttp.client._
import io.circe.generic.auto._
import sttp.client.circe._
import io.circe.syntax._
import io.circe.Encoder
import io.circe.Decoder

object InstacodeApi {
  final case class InstacodePayload(
    language: Language,
    theme: Theme,
    code: Code
  )
  final case class Language(value: String) extends AnyVal
  final case class Theme(value: String) extends AnyVal
  final case class Code(value: String) extends AnyVal

  object Language {
    implicit val encoder: Encoder[Language] = Encoder[String].contramap(_.value)
    implicit val decoder: Decoder[Language] = Decoder[String].map(Language(_))
  }
  object Theme {
    implicit val encoder: Encoder[Theme] = Encoder[String].contramap(_.value)
    implicit val decoder: Decoder[Theme] = Decoder[String].map(Theme(_))
  }
  object Code {
    implicit val encoder: Encoder[Code] = Encoder[String].contramap(_.value)
    implicit val decoder: Decoder[Code] = Decoder[String].map(Code(_))
  }

  /**
  * Вещи вроде адресов было бы хорошо выносить в конфиг приложения
   */
  val apiUrl = "http://instaco.de/api/highlight"

  def mkImage[F[_]](payload: InstacodePayload)(implicit client: SttpUtils.Client[F]) = {
    /**
     *  Для логгирования можно посмотреть, например, в сторону scala-logging
     *  https://github.com/lightbend/scala-logging
     *
     *  Также есть наша tagless-final обёртка в tofu-logging :)
     *  https://github.com/TinkoffCreditSystems/tofu/tree/master/logging
     */
    println(s"Trying to get image with ${apiUrl} for ${payload.asJson}")

    basicRequest
      .post(uri"${apiUrl}")
      .body(payload)
      .send()
  }
}
