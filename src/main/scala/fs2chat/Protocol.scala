package fs2chat

import scodec.Codec
import scodec.codecs._

object Protocol {
  sealed trait Command

  sealed trait ClientCommand extends Command
  object ClientCommand {
    case class ClientHello(name: String) extends ClientCommand
    case class SendMessage(value: String) extends ClientCommand

    val codec: Codec[ClientCommand] = discriminated[ClientCommand]
      .by(uint8)
      .typecase(1, utf8_32.as[ClientHello])
      .typecase(2, utf8_32.as[SendMessage])
  }

  sealed trait ServerCommand extends Command
  object ServerCommand {
    case class SetName(name: String) extends ServerCommand
    case class Alert(message: String) extends ServerCommand
    case class Message(name: String, message: String) extends ServerCommand
    case object Disconnect extends ServerCommand

    val codec: Codec[ServerCommand] = discriminated[ServerCommand]
      .by(uint8)
      .typecase(129, utf8_32.as[SetName])
      .typecase(130, utf8_32.as[Alert])
      .typecase(131, (utf8_32 :: utf8_32).as[Message])
      .typecase(132, provide(Disconnect))
  }
}
