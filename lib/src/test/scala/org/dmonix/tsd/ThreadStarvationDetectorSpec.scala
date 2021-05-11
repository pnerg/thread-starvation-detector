/**
 *  Copyright 2018 Peter Nerg
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.dmonix.tsd

import org.specs2.mutable.Specification

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/**
 * Tests for the [[ThreadStarvationDetector]] class
 * @author Peter Nerg
 */
class ThreadStarvationDetectorSpec extends Specification with TestUtils {
  "Create instance of reporter shall" >> {
    import ThreadStarvationDetector.createReporter
    "return the default logger reporter for the default config" >> {
      val localConfig = config.getConfig("thread-starvation-detector.reporter.logging-reporter")
      createReporter("logger-reporter", localConfig, config) must beSome
    }
    "return the configured custom reporter" >> {
      val localConfig = mockReporterConfig(true).getConfig("thread-starvation-detector.reporter.mock-reporter")
      createReporter("mock-reporter", localConfig, config) must beSome
    }
    "return None if the custom reporter is disabled" >> {
      val localConfig = mockReporterConfig(false).getConfig("thread-starvation-detector.reporter.mock-reporter")
      createReporter("mock-reporter", localConfig, config) must beNone
    }
    "return None for the default config if the logger reporter is disabled" >> {
      val localConfig = config("thread-starvation-detector.reporter.logging-reporter.enabled=false").getConfig("thread-starvation-detector.reporter.logging-reporter")
      createReporter("logger-reporter", localConfig, config) must beNone
    }
  }
  "Creating list of reporters shall" >> {
    import ThreadStarvationDetector.createReporters
    "return the default logger reporter for the default config" >> {
      createReporters(config) must have size(1)
    }
    "return the default logger reporter and added custom/mock logger" >> {
      val localConfig = mockReporterConfig(true).withFallback(config)
      createReporters(localConfig) must have size(2)
    }
    "return only the default logger reporter if the custom/mock logger is disabled" >> {
      val localConfig = mockReporterConfig(false).withFallback(config)
      createReporters(localConfig) must have size(1)
    }
    "yield an empty list for the default config if the logger reporter is disabled" >> {
      val localConfig = config("thread-starvation-detector.reporter.logging-reporter.enabled=false")
      createReporters(localConfig) must beEmpty
    }
    "yield an empty list if there are no configured reporters" >> {
      ok
    }
  }
  "Using ThreadStarvationDetector object shall" >> {
    "yield a no-op monitor if the feature is disabled" >> {
      val localConfig = config("thread-starvation-detector.enabled=false")
      ThreadStarvationDetector(localConfig) must haveClass[NoOpThreadStarvationDetector]
    }
    "yield a proper monitor if the feature is enabled" >> {
      ThreadStarvationDetector(config) must haveClass[ThreadStarvationDetectorImpl]
    }
    "properly parse the default config" >> {
      val cfg = ThreadStarvationDetector.parseConfig(config)
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
      val cfg = ThreadStarvationDetector.parseConfig(config(overrides))
      cfg.checkInterval === 1.second //default value
      cfg.initialDelay === 1.second //custom value
      cfg.defaultMonitorConfig.maxExecutionTimeThreshold === 200.millis //custom value
      cfg.defaultMonitorConfig.loggingEnabled === true //default value
      cfg.defaultMonitorConfig.warningSilenceDuration === 30.seconds //default value
      cfg.customMonitorConfig.get("example-context") must beSome(MonitorConfig(300.millis, 30.seconds, false)) //custom value
    }
  }
  "Using ThreadStarvationDetectorImpl shall" >> {
    val monitorConfig = MonitorConfig(50.millis, 10.seconds, true)
    val config = ThreadStarvationDetectorConfig(0.seconds, 25.millis, monitorConfig, Map.empty)
    "shall detect if a thread pool is choked" >> {
      val reporter = MockReporter()
      val detector = new ThreadStarvationDetectorImpl(config, Seq(reporter))
      val name = "test-example"
      val ec = createExecutionContext(name, 1)
      val monitor = detector.monitorExecutionContext(name, ec)
      monitor.isCancelled === false

      //create a job that hogs the only Thread for 100 ms
      ec.execute(new BlockingJob(100.millis))

      reporter.waitForReport() == true
      detector.stop()
      //all monitors should automatically be cancelled when stopping the detector
      monitor.isCancelled === true
    }
    "return the existing monitor if trying to add a second with the same name" >> {
      val detector = new ThreadStarvationDetectorImpl(config, Seq(MockReporter()))
      val name = "another-test-example"
      val ec = createExecutionContext(name, 1)
      val monitor = detector.monitorExecutionContext(name, ec)
      val monitor2 = detector.monitorExecutionContext(name, ec)

      //shall be the same object instance
      monitor === monitor2
    }
  }

  "Using NoOpThreadStarvationDetector shall" >> {
    "do nothing when monitoring an execution context" >> {
      val detector = new NoOpThreadStarvationDetector()
      val name = "some-name"
      val ec = createExecutionContext(name, 1)
      val monitor = detector.monitorExecutionContext(name, ec)

      //should be cancelled from start
      monitor.isCancelled === true
      monitor.failureCount === 0

      //does nothing
      detector.stop()
      ok
    }
  }

  private class BlockingJob(blockTime:FiniteDuration) extends Runnable {
    override def run(): Unit = {
      Thread.sleep(blockTime.toMillis)
    }
  }

}
