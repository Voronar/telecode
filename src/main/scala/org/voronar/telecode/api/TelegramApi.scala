package org.voronar.telecode

import java.{util => ju}
import sttp.client._
import sttp.model.MediaType
import org.voronar.telecode.BotProtocol.HookInitPayload
import io.circe.generic.auto._
import sttp.client.circe._
import io.circe.syntax._

object TelegramApi {
  val botUrl = "https://api.telegram.org/bot"
  def mkAction(action: String) = s"${botUrl}${EnvConfig.botToken}/${action}"
  def initHook[F[_]: SttpUtils.Client](url: String): F[Response[Either[String, String]]] = {
    println(s"Trying to init hook for ${s"${mkAction("setHook")}"} with ${HookInitPayload(url).asJson}")
    basicRequest
      .post(uri"${mkAction("setWebhook")}")
      .body(HookInitPayload(url))
      .send()
  }

  def sendPhotoBase64[F[_]: SttpUtils.Client](chat_id: Long, imgb64: String): F[Response[Either[String, String]]] = {
    val decoder = ju.Base64.getDecoder()
    val decodedImg = decoder.decode(imgb64)
    val body = List(
      multipart("chat_id", chat_id.toString()),
      multipart("photo", decodedImg)
        .contentType(MediaType.ApplicationOctetStream)
        .fileName("photo.png")
    )
    basicRequest
      .post(uri"${mkAction("sendPhoto")}")
      .multipartBody(body)
      .send()
  }

  def sendMessage[F[_]: SttpUtils.Client](chat_id: Long, text: String): F[Response[Either[String, String]]] =
    basicRequest
      .post(uri"${mkAction("sendMessage")}")
      .body(BotProtocol.SendMessage(chat_id, text))
      .send()
}
