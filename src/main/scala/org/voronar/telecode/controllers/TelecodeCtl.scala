package org.voronar.telecode

import cats.implicits._
import org.voronar.telecode.InstacodeApi.InstacodePayload
import cats.effect.{ConcurrentEffect, IO, LiftIO}
import org.voronar.telecode.BotProtocol.{MessageEntity, MessageType}

// start - Start the bot
// get_themes - Supported theme list
// set_theme - Theme setting
// stop - Stop the bot
object BotCommandNames {
  val Start = s"/start"
  val Stop = s"/stop"
  val SetTheme = s"/set_theme"
  val GetThemes = s"/get_themes"
}

object TelecodeCtlCommands {
  sealed trait Command
  final case class HighlightCode(value: InstacodeApi.InstacodePayload, chatId: Int) extends Command
  final case class SendResponseMessage(value: String, chatId: Int) extends Command
  final case object Noop extends Command

  def noop(): Command = Noop
}

trait TelecodeCtl[F[_]] {
  def parseInputMessage(message: Option[BotProtocol.Message]): F[TelecodeCtlCommands.Command]
  def execCommand(command: TelecodeCtlCommands.Command): F[Unit]
}

object TelecodeCtl {
  val commandSeparator = "\n"

  def impl[F[_]: ConcurrentEffect](
    client: SttpUtils.Client[F],
    proxyClient: SttpUtils.Client[F],
    db: Db
  ): TelecodeCtl[F] = {
    import TelecodeCtlCommands._
    implicit val clientBackend = proxyClient

    new TelecodeCtl[F] {
      def liftF[T](effect: IO[T]) = LiftIO[F].liftIO(effect)
      def getCurrentTheme(chatId: Int): F[String] = liftF(db.getThemeByChatId(chatId))
      def registeredChatAction(chatId: Int)(default: F[Command])(action: => F[Command]): F[Command] =
        for {
          chat <- liftF(db.getChatByChatId(chatId))
          r <- chat match {
            case Some(_) => action
            case None    => default
          }
        } yield r

      def parseTextHighlight(text: String, chatId: Int): F[Command] = {
        val separatorIndex = text.indexOf(commandSeparator)
        val codeAndLanguage =
          if (separatorIndex != -1) {
            val (language, code) = text.splitAt(separatorIndex + 1)
            Some(language.trim(), code)
          } else None

        import InstacodeApi._
        codeAndLanguage match {
          case Some((language, code)) => {
            for {
              currentTheme <- getCurrentTheme(chatId)
            } yield HighlightCode(
              InstacodePayload(
                language = Language(language),
                theme = Theme(currentTheme),
                code = Code(code)
              ),
              chatId
            )
          }

          case None => noop().pure[F]
        }
      }

      def processTextOrNoop(text: Option[String])(cb: String => F[Command]) = text match {
        case Some(value) => cb(value)
        case None        => noop().pure[F]
      }

      def parseInputMessage(message: Option[BotProtocol.Message]): F[Command] =
        for {
          command <- {
            message match {
              case Some(msg) => {
                val chatId = msg.chat.id
                msg.entities match {
                  case Some(List(MessageEntity(_, _, MessageType.BotCommand))) => {
                    processTextOrNoop(msg.text) { text =>
                      text match {
                        case cmd if cmd.startsWith(BotCommandNames.GetThemes) =>
                          for {
                            themes <- liftF(db.getThemes())
                          } yield SendResponseMessage(s"Supported themes:\n\n${themes.mkString("\n")}", chatId)
                        case cmd if cmd.startsWith(BotCommandNames.Start) =>
                          for {
                            chat <- liftF(db.getChatByChatId(chatId))
                            msg <- chat match {
                              case Some(_) => "Bot already started, bro!".pure[F]
                              case None =>
                                liftF(db.createChatByChatId(chatId)) *> "Wellcome to Telecode service, bro!".pure[F]
                            }
                          } yield SendResponseMessage(msg, chatId)
                        case cmd if cmd.startsWith(BotCommandNames.Stop) =>
                          for {
                            chat <- liftF(db.getChatByChatId(chatId))
                            msg <- chat match {
                              case Some(_) =>
                                liftF(db.deleteChatByChatId(chatId)) *> "Thank you for using Telecode service, bro!"
                                  .pure[F]
                              case None => "Bot already stoped, bro!".pure[F]
                            }
                          } yield SendResponseMessage(msg, chatId)
                        case _ => noop().pure[F]
                      }
                    }
                  }
                  // regular message
                  case _ => {
                    processTextOrNoop(msg.text) { text =>
                      registeredChatAction(chatId)(noop().pure[F]) {
                        parseTextHighlight(text, chatId)
                      }
                    }
                  }
                }
              }
              case None => noop().pure[F]
            }
          }
        } yield command

      def execCommand(command: Command): F[Unit] =
        command match {
          case HighlightCode(value, chat_id) =>
            sendImage(
              chat_id,
              value
            )
          case SendResponseMessage(value, chat_id) =>
            sendMessage(
              chat_id,
              value
            )
          case Noop => ().pure[F]
        }

      def sendMessage(chat_id: Int, msg: String): F[Unit] =
        for {
          res <- TelegramApi.sendMessage(chat_id, msg)
          _ <- println(s"[TelecodeCtl.sendMessage] response: ${res.body.getOrElse("nothing")}").pure[F]
        } yield ()

      def sendImage(chat_id: Int, payload: InstacodePayload): F[Unit] =
        for {
          instaResp <- {
            implicit val clientBackend = client
            InstacodeApi.mkImage(payload)
          }
          b64 = instaResp.body.getOrElse("")
          _ <- if (b64 != "") TelegramApi.sendPhotoBase64(chat_id, b64) else ().pure[F]
        } yield ()
    }
  }
}
