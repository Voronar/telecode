package org.voronar.telecode

import java.{util => ju}

/**
 *  Очень здорово, что выбор пал на sttp, т.к. в любой момент можно будет с минимальными
 *  затратами начать использовать другие имплементации http-клиентов.
 */
import sttp.client._
import sttp.model.MediaType
import org.voronar.telecode.BotProtocol.HookInitPayload
import io.circe.generic.auto._
import sttp.client.circe._
import io.circe.syntax._

/**
 *  Имеет смысл разделять разделять все возможные клиенты на интерфейс и реализацию.
 *  Представьте, например, что Вы решили вынести в конфиг `botUrl`. Тогда придется явно его передавать
 *  в каждый метод. Вместо этого можно в начале программы инициализировать имплементацию с этим конфигом:
 */

// интерфейс
trait TelegramApi[F[_]] {
  def initHook(url: String): F[Response[Either[String, String]]]
  // ... other methods
}

// имплементация
case class TelegramApiImpl[F[_]: SttpUtils.Client](botUrl: String) extends TelegramApi[F] {
  def initHook(url: String): F[Response[Either[String, String]]] = {
    println(s"Trying to init hook for ${s"${mkAction("setHook")}"} with ${HookInitPayload(url).asJson}")
    basicRequest
      .post(uri"${mkAction("setWebhook")}")
      .body(HookInitPayload(url))
      .send()
  }

  private def mkAction(action: String) = s"${botUrl}${EnvConfig.botToken}/${action}"
}

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

  def sendPhotoBase64[F[_]: SttpUtils.Client](chat_id: Int, imgb64: String): F[Response[Either[String, String]]] = {
    val decoder = ju.Base64.getDecoder

    /**
    * Нужно быть аккуратным при интеграции с Java. Метод `decode`
     * выбрасывает как минимум
     * `IllegalArgumentException`.
     * Стоит его отловить через `Try` и обработать,
     * а в случае ошибки, например, залоггировать
     */
    val decodedImg = decoder.decode(imgb64)
    val body = List(
      multipart("chat_id", chat_id.toString),
      multipart("photo", decodedImg)
        .contentType(MediaType.ApplicationOctetStream)
        .fileName("photo.png")
    )
    basicRequest
      .post(uri"${mkAction("sendPhoto")}")
      .multipartBody(body)
      .send()
  }

  def sendMessage[F[_]: SttpUtils.Client](chat_id: Int, text: String): F[Response[Either[String, String]]] =
    basicRequest
      .post(uri"${mkAction("sendMessage")}")
      .body(BotProtocol.SendMessage(chat_id, text))
      .send()
}
