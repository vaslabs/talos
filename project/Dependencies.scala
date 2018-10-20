import sbt._
object Dependencies {

  object versions {
    val akka = "2.5.17"
    val circe = "0.10.0"
    val kamon = "1.1.0"
    val scalatest = "3.0.5"
    val akkaHttp = "10.1.5"
  }

  object libraries {
    object Akka {
      val actorTyped = "com.typesafe.akka" %% "akka-actor-typed" % versions.akka
      val actorTestkitTyped = "com.typesafe.akka" %% "akka-actor-testkit-typed" % versions.akka % Test
      val http = "com.typesafe.akka" %% "akka-http"   % versions.akkaHttp
      val httpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % "10.1.5"
      val stream = "com.typesafe.akka" %% "akka-stream" % versions.akka
      val all = Seq(actorTyped, actorTestkitTyped)
      val allHttp = all ++ Seq(stream, http, httpTestkit)
    }

    object Kamon {
      val core = "io.kamon" %% "kamon-core" % versions.kamon
      val all = Seq(core)
    }

    object Circe {
      val all = Seq(
        "io.circe" %% "circe-core",
        "io.circe" %% "circe-generic",
        "io.circe" %% "circe-parser"
      ).map(_ % versions.circe)
    }
    object ScalaTest {
      val all = Seq("org.scalatest" %% "scalatest" % versions.scalatest % Test)
    }
  }



}
