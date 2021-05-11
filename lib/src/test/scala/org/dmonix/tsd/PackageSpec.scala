package org.dmonix.tsd

import org.specs2.mutable.Specification

import scala.concurrent.duration.DurationInt

class PackageSpec extends Specification with TestUtils {

  "Parsing config shall" >> {
    "properly parse the default config" >> {
      val cfg = parseConfig(config)
      cfg.checkInterval === 1.second
      cfg.initialDelay === 30.seconds
      cfg.defaultMonitorConfig.maxExecutionTimeThreshold === 100.millis
      cfg.defaultMonitorConfig.loggingEnabled === true
      cfg.defaultMonitorConfig.warningSilenceDuration === 30.seconds
      cfg.customMonitorConfig must beEmpty
    }
    "properly parse a custom config" >> {
      val overrides =
        """
          |thread-starvation-detector {
          |  enabled = true
          |  initial-delay = 1s
          |  max-execution-time-threshold = 200ms
          |  custom {
          |    #custom config for one of the execution contexts
          |    example-context {
          |      max-execution-time-threshold = 300ms
          |      logging-enabled = false
          |    }
          |  }
          |}
          |""".stripMargin
      val cfg = parseConfig(config(overrides))
      cfg.checkInterval === 1.second //default value
      cfg.initialDelay === 1.second //custom value
      cfg.defaultMonitorConfig.maxExecutionTimeThreshold === 200.millis //custom value
      cfg.defaultMonitorConfig.loggingEnabled === true //default value
      cfg.defaultMonitorConfig.warningSilenceDuration === 30.seconds //default value
      cfg.customMonitorConfig.get("example-context") must beSome(MonitorConfig(300.millis, 30.seconds, false)) //custom value
    }
  }
}
