package org.voronar.telecode

import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.SttpBackend

object SttpUtils {
  type Client[F[_]] = SttpBackend[F, Nothing, WebSocketHandler]
}