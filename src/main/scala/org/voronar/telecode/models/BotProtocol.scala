package org.voronar.telecode

import io.circe.generic.auto._
import io.circe.{Decoder, Encoder}
import org.http4s.circe._
import io.circe.syntax._
import io.circe.HCursor
import cats.effect.ConcurrentEffect

object BotProtocol {
  object MessageType {
    /**
    * Лучше оставить однобуквенные названия для тайп-параметров
    */
    sealed trait T
    final case object BotCommand extends T
    final case object Pre extends T

    /**
     * Не стоит выделять дефолтный тип Undefined. Абсолютно нормально,
     * если обработка сообщений, не поддерживаемых протоколом, будут
     * фейлиться на этапе декодинга
     */
    final case object Undefined extends T

    implicit val encodeMessageType: Encoder[T] = Encoder.instance {
      case BotCommand => "bot_command".asJson
      case Pre        => "pre".asJson
      case Undefined  => "pre".asJson
    }

    implicit val decodeMessageType: Decoder[T] = Decoder[String].map {
        case "bot_command" => BotCommand
        case "pre"         => Pre
        case _             => Undefined
      }
  }

  final case class MessageEntity(
    offset: Int,
    length: Int,
    `type`: MessageType.T // bot_command | pre
  )

  final case class From(
    id: Int,
    is_bot: Boolean,
    first_name: String,
    username: String,
    last_name: Option[String],
    language_code: String,
  )

  final case class Chat(
    id: Int,
    title: Option[String],
    `type`: String,
    first_name: Option[String],
    username: Option[String],
    all_members_are_administrators: Option[Boolean],
  )

  final case class Message(
    message_id: Int,
    from: From,
    chat: Chat,
    date: Int,
    text: Option[String],
    entities: Option[List[MessageEntity]],
  )

  final case class Update(
    update_id: Int,
    message: Option[Message],
  )

  final case class HookInitPayload(
    url: String,
  )

  final case class SendMessage(chat_id: Int, text: String)

  /**
  * `jsonOf` требует `Sync`, дайте ему только его, а не более сильный `ConcurrentEffect`
   */
  implicit def hookUpdateHttp4sDecoder[F[_]: ConcurrentEffect] = jsonOf[F, Update]
  // в названиии decoder, а на самом деле Encoder
  implicit val updateDecoder = Encoder[Update]
}
