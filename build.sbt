organization := "co.fs2"
name := "fs2-chat"

scalaVersion := "2.13.4"

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-io" % "3.0.0-M7",
  "io.chrisdavenport" %% "log4cats-slf4j" % "1.0.1",
  "org.slf4j" % "slf4j-simple" % "1.7.26",
  "com.comcast" %% "ip4s-core" % "1.4.0",
  "org.scodec" %% "scodec-stream" % "3.0-89-8ba5529",
  "org.jline" % "jline" % "3.12.1",
  "com.monovore" %% "decline" % "1.0.0"
)

fork in run := true
outputStrategy := Some(StdoutOutput)
connectInput in run := true

scalafmtOnCompile := true

scalacOptions ++= List(
  "-feature",
  "-language:higherKinds",
  "-Xlint",
  "-Yrangepos",
  "-Ywarn-unused"
)

enablePlugins(UniversalPlugin, JavaAppPackaging)
