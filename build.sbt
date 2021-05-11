import sbt.Keys.{javacOptions, scalaVersion}

val componentVersion = "1.0.0"

organization  := "org.dmonix"
version := componentVersion
name := "thread-starvation-detector"
publishArtifact := false
publishArtifact in (Compile, packageBin) := false
publishArtifact in (Compile, packageDoc) := false
publishArtifact in (Compile, packageSrc) := false

val baseSettings = Seq(
  organization := "org.dmonix",
  version := componentVersion,
  scalaVersion := "2.13.4",
  crossScalaVersions := Seq("2.11.12", "2.12.12", "2.13.4"),
  scalacOptions := Seq("-feature",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-unchecked",
    "-deprecation",
    "-encoding", "utf8"),
  libraryDependencies ++= Seq(
    `slf4j-api`,
    `typesafe-config`,
    `specs2-core` % "test",
    `specs2-mock` % "test",
    `specs2-junit` % "test",
    `specs2-matcher-extra` % "test",
    `logback-classic` % "test"
  )
)

// ======================================================
// The "main" project/library
// ======================================================
lazy val lib = (project in file("lib"))
  .settings(baseSettings)
  .settings(
    name := "thread-starvation-detector"
  )

// ======================================================
// Logging reporter
// ======================================================
/*
lazy val logging = (project in file("logging-reporter"))
  .settings(baseSettings)
  .settings(
    name := "thread-starvation-detector-logging-reporter",
  ).dependsOn(lib)
*/

// ======================================================
// Kamon reporter
// ======================================================
/*
lazy val kamon = (project in file("kamon-reporter"))
  .settings(baseSettings)
  .settings(
    name := "thread-starvation-detector-kamon-reporter",
    libraryDependencies ++= Seq(
      `kamon-core` % "provided"
    )

  ).dependsOn(lib)
*/