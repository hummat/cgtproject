name := "dynamic-colearning"

version := "1.0"

scalaVersion := "2.11.8"

lazy val akkaVersion = "2.5.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "org.vegas-viz" %% "vegas" % "0.3.11"
)
