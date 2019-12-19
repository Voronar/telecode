package org.voronar.telecode

import doobie._
import doobie.implicits._
import cats.effect.IO
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors

object Defaults {
  val theme = "vs"
}

object InputModes {
  val SetTheme = "set_theme"
  val None = ""
}

trait Db[F[_]] {
  def getChatByChatId(chatId: Long): F[Option[Db.ChatConfig]]
  def createChatByChatId(chatId: Long): F[Int]
  def deleteChatByChatId(chatId: Long): F[Int]
  def setInputModeState(chatId: Long, state: String): F[Int]
  def setTheme(chatId: Long, theme: String): F[Int]
  def getThemeByChatId(chatId: Long): F[String]
  def getInputModeState(chatId: Long): F[Option[String]]
  def getThemes(): F[List[String]]
}

object Db {
  final case class ChatConfig(
    id: Long,
    chatId: Long,
    inputMode: String,
    theme: String,
  )

  def impl[F[_]]() = new Db[IO] {
    implicit val cs = IO.contextShift(ExecutionContext.fromExecutor(Executors.newCachedThreadPool()))

    val xa = {
      import EnvConfig._
      Transactor.fromDriverManager[IO](dbDriver, dbUrl, dbUser, dbPass)
    }

    def getChatByChatId(chatId: Long) =
      sql"SELECT * FROM configs WHERE chatId=${chatId}"
        .query[ChatConfig]
        .option
        .transact(xa)
    def createChatByChatId(chatId: Long) =
      sql"""
        INSERT INTO configs
        (chatId, theme)
        VALUES (${chatId}, ${Defaults.theme})
      """.update.run
        .transact(xa)
    def deleteChatByChatId(chatId: Long) =
      sql"""
        DELETE FROM configs
        WHERE chatId=${chatId}
      """.update.run
        .transact(xa)

    def setInputModeState(chatId: Long, state: String) =
      sql"""
        UPDATE configs
        SET inputMode=${state}
        WHERE chatId=${chatId}
      """.update.run
        .transact(xa)

    def getInputModeState(chatId: Long) =
      sql"SELECT inputMode FROM configs WHERE chatId=${chatId}"
      .query[String]
      .option
      .transact(xa)

    def setTheme(chatId: Long, theme: String) =
      sql"""
        UPDATE configs
        SET theme=${theme}
        WHERE chatId=${chatId}
      """.update.run
        .transact(xa)

    def getThemeByChatId(chatId: Long) =
      sql"SELECT theme FROM configs WHERE chatId=${chatId}"
        .query[String]
        .option
        .transact(xa)
        .map(_.getOrElse("vs"))
    def getThemes(): IO[List[String]] = IO { List("vs", "monokai", "solarized", "paraiso-dark") }
  }
}
