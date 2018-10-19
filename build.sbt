import Dependencies._

name := "talos"

version := "0.1"

scalaVersion := "2.12.7"


lazy val talosEvents =
  (project in file("events")).settings(
    libraryDependencies ++= libraries.Akka.all ++ libraries.ScalaTest.all
  )

lazy val talosKamon =
  (project in file("kamon")).settings(
    libraryDependencies ++= libraries.Kamon.all ++ libraries.ScalaTest.all ++ libraries.Akka.all
  ).dependsOn(talosEvents)

lazy val hystrixReporter =
  (project in file("hystrix-reporter")).settings(
    libraryDependencies ++= libraries.Akka.all ++ libraries.Kamon.all ++ libraries.Circe.all
  )

lazy val talos =
  (project in file("."))
    .aggregate(talosEvents, talosKamon)