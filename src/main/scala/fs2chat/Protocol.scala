package fs2chat

import scodec.Codec
import scodec.codecs._

object Protocol {
  sealed trait Command

  private val username: Codec[Username] =
    utf8_32.xmapc(Username.apply)(_.value)

  sealed trait ClientCommand extends Command
  object ClientCommand {
    case class RequestUsername(name: Username) extends ClientCommand
    case class SendMessage(value: String) extends ClientCommand

    val codec: Codec[ClientCommand] = discriminated[ClientCommand]
      .by(uint8)
      .typecase(1, username.as[RequestUsername])
      .typecase(2, utf8_32.as[SendMessage])
  }

  sealed trait ServerCommand extends Command
  object ServerCommand {
    case class SetUsername(name: Username) extends ServerCommand
    case class Alert(text: String) extends ServerCommand
    case class Message(name: Username, text: String) extends ServerCommand
    case object Disconnect extends ServerCommand

    val codec: Codec[ServerCommand] = discriminated[ServerCommand]
      .by(uint8)
      .typecase(129, username.as[SetUsername])
      .typecase(130, utf8_32.as[Alert])
      .typecase(131, (username :: utf8_32).as[Message])
      .typecase(132, provide(Disconnect))
  }
}
