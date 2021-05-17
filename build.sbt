import Dependencies._
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import microsites.ExtraMdFileConfig
import sbt.url

name := "talos"
sonatypeProfileName := "org.vaslabs"
version in ThisBuild := sys.env.getOrElse("VASLABS_PUBLISH_VERSION", "SNAPSHOT")
scalaVersion in ThisBuild := "2.12.13"

lazy val talos =
  (project in file("."))
    .settings(noPublishSettings)
    .aggregate(
      talosCore, talosKamon,
      talosAkkaSupport, talosMonixSupport, talosLaws,
      talosGateway,
      talosExamples,
      compat,
      talosLoadTests
    )


lazy val talosCore =
  (project in file("core"))
  .settings(
    libraryDependencies ++= Seq(libraries.ScalaTest.scalatest % Test, libraries.Cats.effect)
  ).settings(
    compilerSettings
  ).settings(publishSettings)

lazy val talosLaws = (project in file("laws"))
  .settings(
    libraryDependencies ++= libraries.ScalaCheck.all
  ).settings(
  compilerSettings
).settings(
  publishSettings
).dependsOn(talosCore, compat)

lazy val talosMonixSupport = (project in file("monix"))
  .settings(
    libraryDependencies ++= libraries.ScalaTest.all ++ libraries.Monix.all
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
    libraryDependencies ++= Seq(libraries.Kamon.core) ++ libraries.Log4j.required.map(_ % Test) ++
      libraries.ScalaTest.all ++ libraries.Akka.all :+
      libraries.Cats.effect
  ).settings(compilerSettings)
    .settings(publishSettings)
  .dependsOn(talosCore)


lazy val compat =
  (project in file("cross-compat"))
  .settings(
    unmanagedSourceDirectories in Compile += {
      val sharedSourceDir = (baseDirectory in ThisBuild).value / "cross-compat/src/main"
      if (scalaBinaryVersion.value.startsWith("2.13"))
        sharedSourceDir / "scala-2.13"
      else
        sharedSourceDir / "scala-2.12"
    }
  ).settings(noPublishSettings)

lazy val dockerCommonSettings = Seq(
  version in Docker := version.value,
  maintainer in Docker := "Vasilis Nicolaou",
  dockerBaseImage := "openjdk:8-alpine",
  dockerCommands += Cmd("USER", "root"),
    dockerCommands ++= Seq(
    ExecCmd("RUN", "apk", "add", "eudev-dev")
  ),
  dockerExposedPorts := Seq(8080, 9095, 5266),
  maintainer := "vaslabsco@gmail.com",
  dockerUsername := Some("vaslabs"),
  javaAgents += "io.kamon" % "kanela-agent" % "1.0.8"
)

lazy val dockerPlugins = Seq(DockerPlugin, AshScriptPlugin, JavaAppPackaging, UniversalPlugin, JavaAgent)

lazy val talosExamples =
  (project in file("examples"))
  .enablePlugins(dockerPlugins: _*)
  .settings(dockerCommonSettings)
  .settings(
    packageName in Docker := "talos-demo",
  )
  .settings(
    libraryDependencies ++=
        libraries.Akka.allHttp ++
        Seq(libraries.Kamon.bundle, libraries.Kamon.prometheus) ++
        libraries.Circe.all ++ libraries.ScalaTest.all ++
        libraries.Log4j.required
  ).settings(noPublishSettings)
  .settings(
    coverageExcludedPackages := ".*"
  )
  .settings(compilerSettings)
  .dependsOn(talosAkkaSupport, talosKamon)

lazy val noPublishSettings = Seq(
  publish := {},
  skip in publish := true,
  publishLocal := {},
  publishArtifact in Test := false
)

lazy val micrositeSettings = Seq(
  micrositeName := "Talos",
  micrositeDescription := "Lawful circuit breakers in Scala",
  micrositeAuthor := "Vasilis Nicolaou",
  micrositeTwitterCreator := "@vaslabs",
  micrositeGithubOwner := "vaslabs",
  micrositeGithubRepo := "talos",
  micrositeUrl := "https://sbt-kubeyml.vaslabs.org",
  micrositeDocumentationUrl := "/events/events.html",
  micrositeExtraMdFiles := Map(
    file("README.md") -> ExtraMdFileConfig(
      "index.md",
      "home",
      Map("section" -> "home", "position" -> "0", "permalink" -> "/")
    )
  ),
  fork in mdoc := true,
  micrositeTheme := "pattern",
    excludeFilter in ghpagesCleanSite :=
    new FileFilter{
      def accept(f: File) = (ghpagesRepository.value / "CNAME").getCanonicalPath == f.getCanonicalPath
    } || "versions.html"
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
  .dependsOn(talosCore, talosKamon, talosAkkaSupport, talosMonixSupport)

lazy val talosGateway =
  (project in file("gateway"))
  .enablePlugins(dockerPlugins: _*)
  .settings(
    libraryDependencies ++=
      libraries.Akka.allHttp ++
        libraries.ScalaTest.all ++
        Seq(libraries.Kamon.bundle, libraries.Kamon.prometheus) ++
        libraries.Wiremock.all ++
        libraries.Log4j.required :+ libraries.PureConf.core
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
    talosKamon, talosAkkaSupport, talosCore
  )

lazy val talosLoadTests = (project in file("load-tests"))
    .settings(
      libraryDependencies ++= libraries.Gatling.all
    ).enablePlugins(GatlingPlugin)
    .settings(noPublishSettings)
    .settings(compilerSettings)
    .settings(crossScalaVersions in ThisProject := Nil)



addCommandAlias("release", ";project talos ;reload ;+publishSigned ;sonatypeReleaseAll; talosMicrosite/publishMicrosite")
addCommandAlias("reportTestCov", ";project talos; coverageReport; coverageAggregate; codacyCoverage")



lazy val publishSettings = Seq(
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

lazy val compilerSettings = Seq(
  scalacOptions ++= {
    if (scalaVersion.value.startsWith("2.13"))
      sharedFlags
    else
      sharedFlags ++ scala212Flags
  },
  crossScalaVersions in ThisBuild := Seq(scala212, scala213)
)

lazy val scala212 = "2.12.13"
lazy val scala213 = "2.13.6"

lazy val scala212Flags = Seq(
  "-Ypartial-unification",
  "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
  "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
  "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Ywarn-nullary-unit"              // Warn when nullary methods return Unit.
)


lazy val sharedFlags = Seq(
  "-deprecation",
  "-feature",
  "-language:postfixOps",              //Allow postfix operator notation, such as `1 to 10 toList'
  "-language:implicitConversions",
  "-language:higherKinds",
  "-Ywarn-dead-code",                  // Warn when dead code is identified.
  "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
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
