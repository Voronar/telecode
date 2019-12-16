package org.voronar.telecode

import cats.effect.{IO, IOApp}

object Main extends IOApp {
  def run(args: List[String]) =
    TelecodeServer.run[IO]()
}
