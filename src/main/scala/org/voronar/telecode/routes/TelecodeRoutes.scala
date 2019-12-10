package org.voronar.telecode

import cats.effect.Sync
import cats.implicits._
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

object TelecodeRoutes {
  def hookRoute[F[_]: Sync](tCtl: TelecodeCtl[F]): HttpRoutes[F] = {
    import org.voronar.telecode.BotProtocol._
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case req @ POST -> Root / EnvConfig.botHookUrlName / EnvConfig.botToken =>
        for {
          hookResponse <- req.as[Update]
          _ <- println(s"Incoming hook json message ${hookResponse.asJson}").pure[F]
          command <- tCtl.parseInputMessage(hookResponse.message)
          _ <- tCtl.execCommand(command)
          resp <- Ok()
        } yield resp
    }
  }
}
