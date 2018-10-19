import sbt._
object Dependencies {

  object versions {
    val akka = "2.5.17"
    val circe = "0.10.0"
    val kamon = "1.1.0"
  }

  object libraries {
    object Akka {
      val actor = "com.typesafe.akka" %% "akka-actor" % versions.akka
      val actorTest = "com.typesafe.akka" %% "akka-testkit" % versions.akka % Test
      val all = Seq(actor, actorTest)
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
  }



}
