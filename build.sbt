organization := "co.fs2"
name := "fs2-chat"

scalaVersion := "3.6.4"

scalacOptions += "-deprecation"

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-io" % "3.13.0-M2",
  "co.fs2" %% "fs2-scodec" % "3.13.0-M2",
  "org.slf4j" % "slf4j-simple" % "2.0.13",
  "org.jline" % "jline" % "3.26.1",
  "com.monovore" %% "decline" % "2.4.1"
)

run / fork := true
outputStrategy := Some(StdoutOutput)
run / connectInput := true

scalafmtOnCompile := true

enablePlugins(UniversalPlugin, JavaAppPackaging)
