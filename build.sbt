organization := "co.fs2"
name := "fs2-chat"

scalaVersion := "2.13.0"

resolvers += "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/"

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-io" % "2.0.0",
  "io.chrisdavenport" %% "log4cats-slf4j" % "0.4.0-M2",
  "org.slf4j" % "slf4j-simple" % "1.7.26",
  "com.comcast" %% "ip4s-cats" % "1.2.1",
  "org.scodec" %% "scodec-stream" % "2.0.0",
  "org.jline" % "jline" % "3.12.1",
  "com.monovore" %% "decline" % "0.7.0-M0"
)

fork in run := true
outputStrategy := Some(StdoutOutput)
connectInput in run := true

scalafmtOnCompile := true

addCompilerPlugin("org.scalameta" % "semanticdb-scalac_2.13.0" % "4.2.0")
scalacOptions ++= List(
  "-feature",
  "-language:higherKinds",
  "-Xlint",
  "-Yrangepos",
  "-Ywarn-unused"
)
scalafixDependencies in ThisBuild += "com.nequissimus" %% "sort-imports" % "0.1.3"

enablePlugins(UniversalPlugin, JavaAppPackaging)
