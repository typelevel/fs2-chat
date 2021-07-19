organization := "co.fs2"
name := "fs2-chat"

scalaVersion := "3.0.1"

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-io" % "3.0.6",
  "org.slf4j" % "slf4j-simple" % "1.7.26",
  "org.scodec" %% "scodec-stream" % "3.0.1",
  "org.jline" % "jline" % "3.12.1",
  "com.monovore" %% "decline" % "2.1.0"
)

run / fork := true
outputStrategy := Some(StdoutOutput)
run / connectInput := true

scalafmtOnCompile := true

scalacOptions ++= List(
  "-feature",
  "-language:higherKinds"
)

enablePlugins(UniversalPlugin, JavaAppPackaging)
