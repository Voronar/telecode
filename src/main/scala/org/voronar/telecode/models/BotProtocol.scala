package org.voronar.telecode

import io.circe.generic.auto._
import io.circe.{Decoder, Encoder}
import org.http4s.circe._
import io.circe.syntax._
import cats.effect.Sync

object BotProtocol {
  object MessageType {
    sealed trait T
    final case object BotCommand extends T
    final case object Pre extends T
    final case object Code extends T
    final case object PhoneNumber extends T

    implicit val encodeMessageType: Encoder[T] = Encoder.instance {
      case BotCommand => "bot_command".asJson
      case Pre        => "pre".asJson
      case Code       => "code".asJson
      case PhoneNumber       => "phone_number".asJson
    }

    implicit val decodeMessageType: Decoder[T] = Decoder[String].map {
        case "bot_command" => BotCommand
        case "pre"         => Pre
        case "code"        => Code
        case "phone_number"        => PhoneNumber
    }
  }

  final case class MessageEntity(
    offset: Long,
    length: Long,
    `type`: MessageType.T // bot_command | pre
  )

  final case class From(
    id: Long,
    is_bot: Boolean,
    first_name: String,
    username: String,
    last_name: Option[String],
    language_code: Option[String],
  )

  final case class Chat(
    id: Long,
    title: Option[String],
    `type`: String,
    first_name: Option[String],
    username: Option[String],
    all_members_are_administrators: Option[Boolean],
  )

  final case class Message(
    message_id: Long,
    from: From,
    chat: Chat,
    date: Long,
    text: Option[String],
    entities: Option[List[MessageEntity]],
  )

  final case class Update(
    update_id: Long,
    message: Option[Message],
  )

  final case class HookInitPayload(
    url: String,
  )

  final case class SendMessage(chat_id: Long, text: String)

  implicit def hookUpdateHttp4sDecoder[F[_]: Sync] = jsonOf[F, Update]
  implicit val updateEncoder = Encoder[Update]
}
