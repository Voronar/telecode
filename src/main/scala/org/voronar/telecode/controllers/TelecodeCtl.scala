package org.voronar.telecode

import cats.Monad
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

object TcltCmd {
  sealed trait Command
  final case class HighlightCode(value: InstacodeApi.InstacodePayload, chatId: Int) extends Command
  final case class SendResponseMessage(value: String, chatId: Int) extends Command
  final case object Noop extends Command

  def sendResponseMessage(value: String, chatId: Int): Command = SendResponseMessage(value, chatId)
  def noop(): Command = Noop
}

trait TelecodeCtl[F[_]] {
  def parseInputMessage(message: Option[BotProtocol.Message]): F[TcltCmd.Command]
  def execCommand(command: TcltCmd.Command): F[Unit]
}

object TelecodeCtl {
  val commandSeparator = "\n"

  /**
   * `ConcurrentEffect` - очень сильное требовние. С его помощью можно сделать
   *  всё что угодно. Кажется, тут хватит и `Monad`. Нужно стараться избегать
   *  таких универсальных требований как `Concurrent`, `Sync`, `ConcurrentEffect`,
   *  т.к. это убивает профит TF по ограничению набора разрешенных поведений.
   */
  def impl[F[_]: Monad](
    client: SttpUtils.Client[F],
    proxyClient: SttpUtils.Client[F],
    db: Db
  ): TelecodeCtl[F] = {
    import TcltCmd._
    implicit val clientBackend = proxyClient

    new TelecodeCtl[F] {
      /**
       * Тут абстракция протекает. Вместо LiftIO можно использовать tofu-lift
       *  https://github.com/TinkoffCreditSystems/tofu/blob/master/core/src/main/scala/tofu/lift/Unlift.scala
       */
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

        /**
        * Вместо этого локального импорта можно выделить весь функионал `InstacodeApi` в trait
         * и в методе `impl` явно запросить экземпляр. Или даже имлисит
         *
         * def impl[F[_]: Monad: InstacodeApi]() = ???
         *
         * Тогда уже на уровне определения метода будет понятно, что тут может использоваться IntacodeApi
         */
        import InstacodeApi._
        codeAndLanguage match {
          case Some((language, code)) =>
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

          case None => noop().pure[F]
        }
      }

      def processTextOrNoop(text: Option[String])(cb: String => F[Command]) = text match {
        case Some(value) => cb(value)
        case None        => noop().pure[F]
      }

      /**
      * Очень сложно прочитать, что делает этот метод. Может, можно как-то порефакторить, чтобы уменьшить вложенность?
       */
      def parseInputMessage(message: Option[BotProtocol.Message]): F[Command] =
        for {
          command <- {
            message match {
              case Some(msg) => {
                val chatId = msg.chat.id
                processTextOrNoop(msg.text) { text => {
                  lazy val textInputModeHandler = msg.entities match {
                    case Some(List(MessageEntity(_, _, MessageType.BotCommand))) => {
                        text match {
                          case cmd if cmd.startsWith(BotCommandNames.SetTheme) =>
                            registeredChatAction(chatId)(TcltCmd.sendResponseMessage(s"Unauthorized action", chatId).pure[F]) {
                              for {
                                _ <- liftF(db.setInputModeState(chatId, InputModes.SetTheme))
                                themes <- liftF(db.getThemes())
                              } yield SendResponseMessage(s"Please enter on this themes:\n\n${themes.mkString(", ")}", chatId)
                            }
                          case cmd if cmd.startsWith(BotCommandNames.GetThemes) =>
                            for {
                              themes <- liftF(db.getThemes())
                            } yield SendResponseMessage(s"Supported themes:\n\n${themes.mkString("\n")}", chatId)
                          case cmd if cmd.startsWith(BotCommandNames.Start) =>
                            for {
                              chat <- liftF(db.getChatByChatId(chatId))
                              message <- chat match {
                                case Some(_) => "Bot already started, bro!".pure[F]
                                case None =>
                                  liftF(db.createChatByChatId(chatId)) *> "Wellcome to Telecode service, bro!".pure[F]
                              }
                            } yield SendResponseMessage(message, chatId)
                          case cmd if cmd.startsWith(BotCommandNames.Stop) =>
                            for {
                              chat <- liftF(db.getChatByChatId(chatId))
                              message <- chat match {
                                case Some(_) =>
                                  liftF(db.deleteChatByChatId(chatId)) *> "Thank you for using Telecode service, bro!".pure[F]
                                case None => "Bot already stoped, bro!".pure[F]
                              }
                            } yield SendResponseMessage(message, chatId)
                          case _ => noop().pure[F]
                        }
                    }
                    // regular message
                    case _ => {
                      registeredChatAction(chatId)(noop().pure[F]) {
                        parseTextHighlight(text, chatId)
                      }
                    }
                  }
                  // handle command payload input mode
                  for {
                    inputMode <- liftF(db.getInputModeState(chatId))
                    res <- inputMode match {
                      case Some("set_theme") => for {
                        themes <- liftF(db.getThemes())
                        cmd <- for {
                          message <- {
                            if (themes.exists(_ == text)) liftF(db.setTheme(chatId, text)) *> s"New theme successfully setted!".pure[F]
                            else s"Unsupported theme! Canceling theme input mode.".pure[F]
                          }
                          _ <- liftF(db.setInputModeState(chatId, InputModes.None))
                        } yield  SendResponseMessage(message, chatId)
                      } yield cmd
                      case _ => textInputModeHandler
                    }
                  } yield res
                }}
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
          // _ <- println("foo").pure[F]
          // ничем не отличается от
          // _ = println("foo")
          _ <- println(s"[TelecodeCtl.sendMessage] response: ${res.body.getOrElse("nothing")}").pure[F]
        } yield ()

      def sendImage(chat_id: Int, payload: InstacodePayload): F[Unit] =
        for {
        // можно не объявлять локальный имплисит, а передать явно:
          instaResp <- InstacodeApi.mkImage(payload)(client)
          b64 = instaResp.body.getOrElse("")
          _ <- if (b64 != "") TelegramApi.sendPhotoBase64(chat_id, b64) else ().pure[F]
        } yield ()
    }
  }
}
