package org.voronar.telecode

import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client._
import cats.effect.ExitCode
import cats.implicits._
import cats.effect.Resource

object TelecodeServer {
  def run[F[_]: ConcurrentEffect: ContextShift: Timer](): F[ExitCode] = {
    val sttpProxyClient = Resource.make {
      AsyncHttpClientCatsBackend(
        options = SttpBackendOptions.socksProxy(EnvConfig.clientProxyHost, EnvConfig.clientProxyPort)
      )
    }(client => client.close())
    val sttpClient = Resource.make(AsyncHttpClientCatsBackend())(client => client.close())

    def initHook[F[_]](implicit client: SttpUtils.Client[F]) =  {
      TelegramApi.initHook(EnvConfig.tempHookEchoServer)
    }

    val mainResource = for {
      client <- sttpClient
      proxyClient <- sttpProxyClient
      initResponse <- Resource.liftF(initHook(proxyClient))
      _ <- initResponse.body match {
        case Right(v) => Resource.liftF(println("Hook init OK: ", v).pure[F])
        case Left(v)  => Resource.liftF(println("Hook init ERROR: ", v).pure[F])
      }
      tctl = TelecodeCtl.impl[F](client, proxyClient, Db.impl())
      httpApp = (
        TelecodeRoutes.hookRoute[F](tctl)
      ).orNotFound
      finalHttpApp = Logger.httpApp(true, true)(httpApp)
      exitCode <- Resource.liftF(BlazeServerBuilder[F]
        .bindHttp(EnvConfig.serverPort, EnvConfig.serverHost)
        .withHttpApp(finalHttpApp)
        .serve.compile.toList)
    } yield exitCode.headOption.getOrElse(ExitCode.Error)

    mainResource.use(v => v.pure[F])
  }
}
