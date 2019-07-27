# FS2 Chat

This repository contains a sample project that demonstrates the use of FS2 and related libraries by building a TCP based chat server app and client app.

## Usage

To start the server application, run `sbt "runMain fs2chat.ServerApp"`. This will start a server on TCP port 5555. Alternatively, run `sbt "runMain fs2chat.ServerApp --port <alternatePort>"` to run on a different port.

To start the client application, run `sbt "runMain fs2chat.ClientApp"`. This will start a client that attempts to connect to 5555 on localhost. Run `sbt "runMain fs2chat.ClientApp --address <serverIp>"` if running the server on a different host.