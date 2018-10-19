import Dependencies._

name := "talos"

version := "0.1"

scalaVersion := "2.12.7"


lazy val events =
  (project in file("talos-events")).settings(
    libraryDependencies ++= libraries.Akka.all ++ libraries.ScalaTest.all
  )

lazy val kamon =
  (project in file("talos-kamon")).settings(
    libraryDependencies ++= libraries.Kamon.all ++ libraries.ScalaTest.all ++ libraries.Akka.all
  ).dependsOn(events)

lazy val hystrixReporter =
  (project in file("hystrix-reporter")).settings(
    libraryDependencies ++= libraries.Akka.all ++ libraries.Kamon.all ++ libraries.Circe.all
  )