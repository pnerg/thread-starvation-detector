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
    "return the configured custom reporter" >> {
      val localConfig = mockReporterConfig(true).withFallback(config)
      createReporter("mock-reporter", localConfig) must beSome
    }
    "return None if the custom reporter is disabled" >> {
      val localConfig = mockReporterConfig(false).withFallback(config)
      createReporter("mock-reporter", localConfig) must beNone
    }
  }
  "Creating list of reporters shall" >> {
    import ThreadStarvationDetector.createReporters
    "yield a empty list if there are no configured reporters" >> {
      createReporters(config) must beEmpty
    }
    "return the added custom/mock logger" >> {
      val localConfig = mockReporterConfig(true).withFallback(config)
      createReporters(localConfig) must have size(1)
    }
    "return a empty list if the custom/mock logger is disabled" >> {
      val localConfig = mockReporterConfig(false).withFallback(config)
      createReporters(localConfig) must beEmpty
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
