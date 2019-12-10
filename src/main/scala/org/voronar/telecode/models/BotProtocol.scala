package org.voronar.telecode

import io.circe.generic.auto._
import io.circe.{Decoder, Encoder}
import org.http4s.circe._
import io.circe.syntax._
import io.circe.HCursor
import cats.effect.ConcurrentEffect

object BotProtocol {
  object MessageType {
    sealed trait T
    final case object BotCommand extends T
    final case object Pre extends T
    final case object Undefined extends T

    implicit val encodeMessageType: Encoder[T] = Encoder.instance {
      case BotCommand => "bot_command".asJson
      case Pre        => "pre".asJson
      case Undefined  => "pre".asJson
    }

    implicit val decodeMessageType: Decoder[T] = Decoder.instance { (hCursor: HCursor) =>
      hCursor.as[String].map {
        case "bot_command" => BotCommand
        case "pre"         => Pre
        case _             => Undefined
      }
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

  implicit def hookUpdateHttp4sDecoder[F[_]: ConcurrentEffect] = jsonOf[F, Update]
  implicit val updateDecoder = Encoder[Update]
}
