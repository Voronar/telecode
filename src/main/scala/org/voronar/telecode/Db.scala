package org.voronar.telecode

import java.util.concurrent.Executors

import doobie._
import doobie.implicits._
import cats.effect.IO

import scala.concurrent.ExecutionContext

object Defaults {
  val theme = "vs"
}

object InputModes {
  val SetTheme = "set_theme"
  val None = ""
}

/**
 *  trait можно сделать с `F[_]`, а уже в имплементации будет `IO`.
 *  Так весь код будет использоватьт некий абстрактный DB[F], и можно будет
 *  легко переезжать, например, на ZIO, просто сделав имплементацию.
 */
trait Db {
  def getChatByChatId(chatId: Int): IO[Option[Db.ChatConfig]]
  def createChatByChatId(chatId: Int): IO[Int]
  def deleteChatByChatId(chatId: Int): IO[Int]
  def setInputModeState(chatId: Int, state: String): IO[Int]
  def setTheme(chatId: Int, theme: String): IO[Int]
  def getThemeByChatId(chatId: Int): IO[String]
  def getInputModeState(chatId: Int): IO[Option[String]]
  def getThemes(): IO[List[String]]
}

object Db {
  final case class ChatConfig(
    id: Int,
    chatId: Int,
    inputMode: String,
    theme: String,
  )

  // F[_] никак не используется
  def impl[F[_]]() = new Db {
    /**
     * ContextShift в данном случае необходим, потому что
     * запросы блокирующие. И такие запросы принято исполнять на отдельном тред-пуле.
     * Если запросов к СУБД накопится слишком много, забьётся весь тред-пул, на котором
     * работает приложение, и оно просто зависнет в ожидании.
     * Поэтому вместо `ExecutionContext.global` надо создать новый:
     * `ExecutionContext.fromExecutor(Executors.newCachedThreadPool())`
     */

    implicit val cs = IO.contextShift(ExecutionContext.global)

    val xa = {
      import EnvConfig._
      Transactor.fromDriverManager[IO](dbDriver, dbUrl, dbUser, dbPass)
    }

    def getChatByChatId(chatId: Int) =
      sql"SELECT * FROM configs WHERE chatId=${chatId}"
        .query[ChatConfig]
        .option
        .transact(xa)
    def createChatByChatId(chatId: Int) =
      sql"""
        INSERT INTO configs
        (chatId, theme)
        VALUES (${chatId}, ${Defaults.theme})
      """.update.run
        .transact(xa)
    def deleteChatByChatId(chatId: Int) =
      sql"""
        DELETE FROM configs
        WHERE chatId=${chatId}
      """.update.run
        .transact(xa)

    def setInputModeState(chatId: Int, state: String) =
      sql"""
        UPDATE configs
        SET inputMode=${state}
        WHERE chatId=${chatId}
      """.update.run
        .transact(xa)

    def getInputModeState(chatId: Int) =
      sql"SELECT inputMode FROM configs WHERE chatId=${chatId}"
      .query[String]
      .option
      .transact(xa)

    def setTheme(chatId: Int, theme: String) =
      sql"""
        UPDATE configs
        SET theme=${theme}
        WHERE chatId=${chatId}
      """.update.run
        .transact(xa)

    def getThemeByChatId(chatId: Int) =
      sql"SELECT theme FROM configs WHERE chatId=${chatId}"
        .query[String]
        .option
        .transact(xa)
        .map(_.getOrElse("vs"))
    def getThemes(): IO[List[String]] = IO { List("vs", "monokai", "solarized", "paraiso-dark") }
  }
}
