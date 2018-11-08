import sbt._
object Dependencies {

  object versions {
    val pureconfig: String = "0.10.0"

    val catsEffect: String = "1.0.0"

    val akka = "2.5.17"
    val circe = "0.10.0"
    val kamon = "1.1.0"
    val scalatest = "3.0.5"
    val akkaHttp = "10.1.5"
    val monix = "3.0.0-RC1"
  }

  object libraries {
    object Akka {
      val actorTyped = "com.typesafe.akka" %% "akka-actor-typed" % versions.akka
      val actorTestkitTyped = "com.typesafe.akka" %% "akka-actor-testkit-typed" % versions.akka % Test
      val http = "com.typesafe.akka" %% "akka-http"   % versions.akkaHttp
      val httpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % versions.akkaHttp
      val streamTestKit = "com.typesafe.akka" %% "akka-stream-testkit" % versions.akka
      val stream = "com.typesafe.akka" %% "akka-stream" % versions.akka
      val all = Seq(actorTyped, actorTestkitTyped)
      val allHttp = all ++ Seq(stream, http, httpTestkit, streamTestKit)
    }

    object Monix {
      val monix = "io.monix" %% "monix" % versions.monix
    }

    object Kamon {
      val core = "io.kamon" %% "kamon-core" % versions.kamon
      val all = Seq(core)
    }

    object Circe {
      val all = Seq(
        "io.circe" %% "circe-core",
        "io.circe" %% "circe-generic",
        "io.circe" %% "circe-parser",
        "io.circe" %% "circe-java8"
      ).map(_ % versions.circe)
    }
    object Cats {
      val effect = "org.typelevel" %% "cats-effect" % versions.catsEffect
    }
    object ScalaTest {
      val all = Seq("org.scalatest" %% "scalatest" % versions.scalatest % Test)
    }

    object PureConf {
      val core = "com.github.pureconfig" %% "pureconfig" % versions.pureconfig
    }
  }



}
