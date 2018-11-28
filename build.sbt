import Dependencies._
import microsites.ExtraMdFileConfig

name := "talos"
sonatypeProfileName := "org.vaslabs"
version in ThisBuild := sys.env.getOrElse("VASLABS_PUBLISH_VERSION", "SNAPSHOT")

val publishSettings = Seq(
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  organization := "org.vaslabs.talos",
  organizationName := "vaslabs",
  scmInfo := Some(ScmInfo(url("https://github.com/vaslabs/talos"), "scm:git@github.com:vaslabs/talos.git")),
  developers := List(
    Developer(
      id    = "vaslabs",
      name  = "Vasilis Nicolaou",
      email = "vaslabsco@gmail.com",
      url   = url("http://vaslabs.org")
    )
  ),
  publishMavenStyle := true,
  licenses := List("MIT" -> new URL("https://opensource.org/licenses/MIT")),
  homepage := Some(url("https://talos.vaslabs.org")),
  startYear := Some(2018)
)

scalaVersion := "2.12.7"

lazy val compilerSettings = {
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-language:postfixOps",              //Allow postfix operator notation, such as `1 to 10 toList'
    "-language:implicitConversions",
    "-language:higherKinds",
    "-Ypartial-unification",
    "-Ywarn-dead-code",                  // Warn when dead code is identified.
    "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
    "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
    "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
    "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
    "-Ywarn-numeric-widen",              // Warn when numerics are widened.
    "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals",              // Warn if a local definition is unused.
    "-Ywarn-unused:params",              // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates",            // Warn if a private member is unused.
    "-Ywarn-value-discard",               // Warn when non-Unit expression results are unused.
    "-Ywarn-unused:imports",
    "-Xfatal-warnings"
  )
}


lazy val talosCore =
  (project in file("core"))
  .settings(
    libraryDependencies ++= Seq(libraries.ScalaTest.scalatest, libraries.Cats.effect, libraries.ScalaTest.scalacheck)
  ).settings(
    compilerSettings
  ).settings(publishSettings)

lazy val talosLaws = (project in file("laws"))
  .settings(
    libraryDependencies ++= libraries.ScalaTest.all
  ).settings(
  compilerSettings
).settings(
  publishSettings
).dependsOn(talosCore)

lazy val talosMonixSupport = (project in file("monix"))
  .settings(
    libraryDependencies ++=
      Seq(libraries.Akka.actorTyped, libraries.Akka.actorTestkitTyped)
        ++ libraries.ScalaTest.all ++ libraries.Monix.all
  )
  .settings(compilerSettings)
  .settings(publishSettings)
  .dependsOn(talosCore, talosLaws % Test)

lazy val talosAkkaSupport = (project in file("akka"))
  .settings(
    libraryDependencies ++= libraries.Akka.all ++ libraries.ScalaTest.all :+ libraries.Cats.effect
  ).settings(compilerSettings)
  .settings(publishSettings)
  .dependsOn(talosCore, talosLaws % Test)

lazy val talosKamon =
  (project in file("kamon")).settings(
    libraryDependencies ++= libraries.Kamon.all ++ libraries.ScalaTest.all ++ libraries.Akka.all :+
      libraries.Cats.effect
  ).settings(compilerSettings)
    .settings(publishSettings)
  .dependsOn(talosCore)

lazy val hystrixReporter =
  (project in file("hystrix-reporter")).settings(
    libraryDependencies ++=
      libraries.Akka.allHttp ++ libraries.Kamon.all ++ libraries.Circe.all ++ libraries.ScalaTest.all
        ++ libraries.Log4j.required.map(_ % Test)
  ).settings(compilerSettings)
    .settings(publishSettings)
  .dependsOn(talosKamon)

lazy val dockerCommonSettings = Seq(
  version in Docker := version.value,
  maintainer in Docker := "Vasilis Nicolaou",
  dockerBaseImage := "openjdk:8-alpine",
  dockerExposedPorts := Seq(8080),
  maintainer := "vaslabsco@gmail.com",
  dockerUsername := Some("vaslabs"),
)

lazy val dockerPlugins = Seq(DockerPlugin, AshScriptPlugin, JavaAppPackaging, UniversalPlugin)

lazy val talosExamples =
  (project in file("examples"))
  .enablePlugins(dockerPlugins: _*)
  .settings(dockerCommonSettings)
  .settings(
    packageName in Docker := "talos-demo",
  )
  .settings(
    libraryDependencies ++=
      libraries.Akka.allHttp ++ libraries.Kamon.all ++ libraries.Circe.all ++ libraries.ScalaTest.all
  ).settings(noPublishSettings)
  .settings(
    coverageExcludedPackages := ".*"
  )
  .dependsOn(hystrixReporter, talosAkkaSupport)

lazy val noPublishSettings = Seq(
  publish := {},
  skip in publish := true,
  publishLocal := {},
  publishArtifact in Test := false
)

lazy val micrositeSettings = Seq(
  micrositeName := "Talos",
  micrositeDescription := "Monitoring tools for Akka circuit breakers",
  micrositeAuthor := "Vasilis Nicolaou",
  micrositeTwitterCreator := "@vaslabs",
  micrositeGithubOwner := "vaslabs",
  micrositeGithubRepo := "talos",
  micrositeBaseUrl := "/talos",
  micrositeDocumentationUrl := "/talos/events/events.html",
  micrositeExtraMdFiles := Map(
    file("README.md") -> ExtraMdFileConfig(
      "index.md",
      "home",
      Map("section" -> "home", "position" -> "0")
    )
  ),
  fork in tut := true,
  micrositePalette := Map(
    "brand-primary" -> "#E05236",
    "brand-secondary" -> "#3F3242",
    "brand-tertiary" -> "#2D232F",
    "gray-dark" -> "#453E46",
    "gray" -> "#837F84",
    "gray-light" -> "#E3E2E3",
    "gray-lighter" -> "#F4F3F4",
    "white-color" -> "#FFFFFF")
)

lazy val talosMicrosite = (project in file("site"))
  .enablePlugins(MicrositesPlugin)
  .settings(noPublishSettings)
  .settings(
    git.remoteRepo := "git@github.com:vaslabs/talos.git"
  )
  .settings(micrositeSettings)
  .settings(
    coverageExcludedPackages := ".*"
  )
  .dependsOn(talosCore, talosKamon, hystrixReporter, talosAkkaSupport, talosMonixSupport)

lazy val talosGateway =
  (project in file("gateway"))
  .enablePlugins(dockerPlugins: _*)
  .enablePlugins(GatlingPlugin)
  .settings(
    libraryDependencies ++=
      libraries.Akka.allHttp ++ libraries.ScalaTest.all ++
        libraries.Gatling.all ++ libraries.Wiremock.all ++ libraries.Log4j.required :+ libraries.PureConf.core
  )
  .settings(
    packageName in Docker := "talos-gateway",
  )
  .settings(
    compilerSettings
  )
  .settings(
    noPublishSettings
  ).settings(
    dockerCommonSettings
  ).dependsOn(
    hystrixReporter, talosKamon, talosAkkaSupport, talosCore
  )

lazy val talos =
  (project in file("."))
  .settings(noPublishSettings)
  .aggregate(
    talosCore, talosKamon, hystrixReporter,
    talosExamples, talosAkkaSupport, talosMonixSupport, talosGateway
  )

addCommandAlias("release", ";project talos ;reload ;+publishSigned ;sonatypeReleaseAll; talosMicrosite/publishMicrosite")
addCommandAlias("reportTestCov", ";project talos; coverageReport; coverageAggregate; codacyCoverage")