organization := "co.fs2"
name := "fs2-chat"

scalaVersion := "3.1.0"

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-io" % "3.2.3",
  "co.fs2" %% "fs2-scodec" % "3.2.3",
  "org.slf4j" % "slf4j-simple" % "1.7.26",
  "org.jline" % "jline" % "3.12.1",
  "com.monovore" %% "decline" % "2.1.0"
)

run / fork := true
outputStrategy := Some(StdoutOutput)
run / connectInput := true

scalafmtOnCompile := true

enablePlugins(UniversalPlugin, JavaAppPackaging)
