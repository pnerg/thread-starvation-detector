import sbt._


object Dependencies extends AutoPlugin {

  object autoImport {
    /**
     * ------------------------------
     * Compile/hard dependencies
     * ------------------------------
     */
    val `slf4j-api`         = "org.slf4j" % "slf4j-api" % "1.7.30"
    val `typesafe-config`   = "com.typesafe" % "config" % "1.4.1"

    val `kamon-core`       = "io.kamon" %% "kamon-core" % "2.1.17"

    /**
     * ------------------------------
     * Test dependencies
     * ------------------------------
     */
    val `logback-classic` = "ch.qos.logback" % "logback-classic" % "1.2.3"

    val `specs-core-version`   = "4.10.6"
    val `specs2-core`          = "org.specs2" %% "specs2-core" % `specs-core-version`
    val `specs2-mock`          = "org.specs2" %% "specs2-mock" % `specs-core-version`
    val `specs2-junit`         = "org.specs2" %% "specs2-junit" % `specs-core-version`
    val `specs2-matcher-extra` = "org.specs2" %% "specs2-matcher-extra" % `specs-core-version`
  }

}
