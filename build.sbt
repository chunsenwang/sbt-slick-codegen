import sbt.addSbtPlugin

val dockerJavaVersion = "3.3.4"
val postgresqlVersion = "42.6.0"

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    version := "0.1.1-SNAPSHOT",
    versionScheme := Some("semver-spec"),
    name := "sbt-slick-codegen",
    organization := "com.tubitv",
    sbtPlugin := true,
    libraryDependencies ++= Seq(
      "org.postgresql" % "postgresql" % postgresqlVersion,
      "com.github.docker-java" % "docker-java" % dockerJavaVersion,
    ),
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    publishTo := {
      if (isSnapshot.value) Some("sbt-dev" at "https://tubins.jfrog.io/tubins/sbt-dev-local")
      else Some("sbt-release" at "https://tubins.jfrog.io/tubins/sbt-release-local")
    },
    addSbtPlugin("com.github.tototoshi" % "sbt-slick-codegen" % "2.0.0"),
    addSbtPlugin("io.github.davidmweber" % "flyway-sbt" % "7.4.0"),
  )
