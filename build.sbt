name := "agenty projekt"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "org.scalaz" %% "scalaz-core" % "7.1.2",
  "io.spray" %% "spray-client" % "1.3.3",
  "io.spray" %% "spray-json" % "1.3.2",
  "io.spray" %% "spray-can" % "1.3.3",
  "io.spray" %% "spray-routing" % "1.3.3",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.11",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "io.github.morgaroth" %% "spray-json-annotation" % "0.4.2",
  "io.github.morgaroth" %% "utils-mongodb" % "1.2.9"
)

Revolver.settings

addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full)

enablePlugins(JavaAppPackaging)