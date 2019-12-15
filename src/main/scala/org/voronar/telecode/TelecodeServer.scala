package org.voronar.telecode

import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import fs2.Stream
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client._

object TelecodeServer {
  /**
   * В данном случае использование Stream не необходимо, так как нет потока данных.
   * `Stream[F, Nothing]` - стрим, не возвращающий элементов, не несёт практической пользы.
   * Достаточно использовать некоторый эффект F[_].
   * Пусть метод возвращает, например F[ExitCode], а не отбрасывает его через `.drain` в конце
   */
  def stream[F[_]: ConcurrentEffect](implicit T: Timer[F], C: ContextShift[F]): Stream[F, Nothing] = {
    val sttpProxyClient = Stream.eval {
      /**
      *  `AsyncHttpClientCatsBackend` - это ресурс, который нуждается в вызове метода
       *  `close` по окончании работы. Поэтому стоит использовать `Stream.resource` или
       *  `cats.effect.Resource`
       */
      AsyncHttpClientCatsBackend(
        options = SttpBackendOptions.socksProxy(EnvConfig.clientProxyHost, EnvConfig.clientProxyPort)
      )
    }
    val sttpClient = Stream.eval { AsyncHttpClientCatsBackend() }

    def initHook[F[_]](implicit client: SttpUtils.Client[F]) = Stream.eval {
      TelegramApi.initHook(EnvConfig.tempHookEchoServer)
    }

    for {
      client <- sttpClient
      proxyClient <- sttpProxyClient
      initResponse <- initHook(proxyClient)
      _ <- initResponse.body match {
        case Right(v) => Stream { println("Hook init OK: ", v) }
        case Left(v)  => Stream { println("Hook init ERROR: ", v) }
      }
      tctl = TelecodeCtl.impl[F](client, proxyClient, Db.impl())
      httpApp = (
        TelecodeRoutes.hookRoute[F](tctl)
      ).orNotFound
      finalHttpApp = Logger.httpApp(true, true)(httpApp)
      exitCode <- BlazeServerBuilder[F]
        .bindHttp(EnvConfig.serverPort, EnvConfig.serverHost)
        .withHttpApp(finalHttpApp)
        .serve
    } yield exitCode
  }.drain
}
