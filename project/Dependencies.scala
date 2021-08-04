import sbt._

object Dependencies {

  object versions {
    val scalacheckshapeless: String = "1.2.5"

    val scalacheck: String = "1.15.2"

    val pureconfig: String = "0.14.1"

    val catsEffect: String = "2.5.3"

    val akka = "2.6.15"
    val circe = "0.13.0"
    val kamon = "2.1.18"
    val scalatest = "3.0.8"
    val akkaHttp = "10.2.3"
    val monix = "3.3.0"
    val gatling = "3.1.0"
    val wiremock = "2.27.2"
    val log4j = "2.10.0"
    val scalalogging = "3.9.3"
  }

  object libraries {

    object Akka {
      val actorTyped = "com.typesafe.akka" %% "akka-actor-typed" % versions.akka
      val slf4j = "com.typesafe.akka" %% "akka-slf4j" % versions.akka
      val streams = "com.typesafe.akka" %% "akka-stream-typed" % versions.akka
      val actorTestkitTyped = "com.typesafe.akka" %% "akka-actor-testkit-typed" % versions.akka % Test
      val http = "com.typesafe.akka" %% "akka-http" % versions.akkaHttp
      val httpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % versions.akkaHttp
      val streamTestKit = "com.typesafe.akka" %% "akka-stream-testkit" % versions.akka
      val all = Seq(actorTyped, actorTestkitTyped)
      val allHttp = all ++ Seq(streams, http, httpTestkit, streamTestKit)
    }

    object Monix {
      val core = "io.monix" %% "monix" % versions.monix
      val catnip = "io.monix" %% "monix" % versions.monix
      val all = Seq(core, catnip)
    }

    object Log4j {
      private val logback = "ch.qos.logback" % "logback-classic" % "1.2.4"
      private val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % versions.scalalogging
      val required = Seq(logback, scalaLogging, Akka.slf4j)
    }

    object Kamon {
      val core = "io.kamon" %% "kamon-core" % versions.kamon
      val prometheus = "io.kamon" %% "kamon-prometheus" % versions.kamon
      val bundle = "io.kamon" %% "kamon-bundle" % versions.kamon
    }

    object Circe {
      val all = Seq(
        "io.circe" %% "circe-core",
        "io.circe" %% "circe-generic",
        "io.circe" %% "circe-parser"
      ).map(_ % versions.circe)
    }

    object Cats {
      val effect = "org.typelevel" %% "cats-effect" % versions.catsEffect
    }

    object ScalaTest {
      val scalatest = "org.scalatest" %% "scalatest" % versions.scalatest
      val all = Seq(scalatest % Test)
    }

    object ScalaCheck {
      val scalacheck = "org.scalacheck" %% "scalacheck" % versions.scalacheck
      val scalacheckshapeless = "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % versions.scalacheckshapeless
      val all = Seq(scalacheck, scalacheckshapeless, ScalaTest.scalatest)
    }

    object PureConf {
      val core = "com.github.pureconfig" %% "pureconfig" % versions.pureconfig
    }

    object Wiremock {
      val wiremock = "com.github.tomakehurst" % "wiremock" % versions.wiremock

      val all = Seq(wiremock % Test)
    }

    object Gatling {
      val charts = "io.gatling.highcharts" % "gatling-charts-highcharts" % versions.gatling % IntegrationTest
      val framework = "io.gatling" % "gatling-test-framework" % versions.gatling % IntegrationTest
      val all = Seq(charts, framework)
    }

  }


}
