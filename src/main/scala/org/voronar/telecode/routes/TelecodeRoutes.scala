package org.voronar.telecode

import cats.effect.ConcurrentEffect
import cats.implicits._
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

object TelecodeRoutes {
  def hookRoute[F[_]: ConcurrentEffect](tCtl: TelecodeCtl[F]): HttpRoutes[F] = {
    import org.voronar.telecode.BotProtocol._
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case req @ POST -> Root / EnvConfig.botHookUrlName / EnvConfig.botToken =>
        for {
          hookResponse <- req.as[Update].map(_.asRight[String]).handleError(e => Left(s"Decode error: ${e.toString()}"))
          _ <- hookResponse match {
            case Left(e) => println(e).pure[F]
            case Right(v) => for {
              _ <- println(s"Incoming hook json message ${v.asJson}").pure[F]
              command <- tCtl.parseInputMessage(v.message)
              _ <- tCtl.execCommand(command)
            } yield ()
          }
          resp <- Ok()
        } yield resp
    }
  }
}
