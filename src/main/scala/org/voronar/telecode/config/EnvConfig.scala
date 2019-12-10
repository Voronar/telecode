package org.voronar.telecode

object EnvConfig {
  def getVariable(name: String) = sys.env.get(name) match {
    case Some(value) => value
    case None        => throw new Exception(s"Required env variable: '${name}'")
  }
  lazy val botName = getVariable("telecode_bot")
  lazy val botToken = getVariable("botToken")
  lazy val botHookUrlName = "bothook"
  lazy val tempHookEchoServerHash = getVariable("tempHookEchoServerHash")
  lazy val tempHookEchoServer = s"https://${tempHookEchoServerHash}.ngrok.io/${botHookUrlName}/${botToken}"

  lazy val clientProxyHost = sys.env.getOrElse("clientProxyHost", "127.0.0.1")
  lazy val clientProxyPort = sys.env.getOrElse("clientProxyPort", "9050").toInt

  lazy val serverHost = "127.0.0.1"
  lazy val serverPort = 8443

  lazy val dbDriver = "org.postgresql.Driver"
  lazy val dbUrl = "jdbc:postgresql:telecode"
  lazy val dbUser = "telecode"
  lazy val dbPass = "telecode_pass"
}
