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

import com.typesafe.config.ConfigFactory
import org.specs2.control.NamedThreadFactory
import org.specs2.mutable.Specification

import java.util.concurrent.{Executors, Semaphore, TimeUnit}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{DurationInt, FiniteDuration}

/**
 * Tests for the [[ThreadStarvationDetector]] class
 * @author Peter Nerg
 */
class ThreadStarvationDetectorSpec extends Specification {
  private val config = ConfigFactory.defaultReference()
  
  "Using ThreadStarvationDetectorSingleton shall" >> {
    val singleton = ThreadStarvationDetectorSingleton.init(config)
    "return the singleton instance if already initiated" >> {
      ThreadStarvationDetectorSingleton.init(config) === singleton
    }
    "return the singleton for every get" >> {
      ThreadStarvationDetectorSingleton.get() === singleton
    }
  }
  "Using ThreadStarvationDetector object shall" >> {
    "yield a no-op monitor if the feature is disabled" >> {
      val localConfig = ConfigFactory.parseString("thread-starvation-detector.enabled=false").withFallback(config)
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
      val cfg = ThreadStarvationDetector.parseConfig(ConfigFactory.parseString(overrides).withFallback(config))
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
      val consumer = new BlockingWarningConsumer()
      val detector = new ThreadStarvationDetectorImpl(config, consumer.warningConsumer)
      val name = "test-example"
      val ec = createExecutionContext(name, 1)
      val monitor = detector.monitorExecutionContext(name, ec)
      monitor.isCancelled === false

      //create a job that hogs the only Thread for 100 ms
      ec.execute(new BlockingJob(100.millis))

      consumer.waitForWarningEvent() === true
      detector.stop()
      //all monitors should automatically be cancelled when stopping the detector
      monitor.isCancelled === true
    }
    "return the existing monitor if trying to add a second with the same name" >> {
      val consumer = new BlockingWarningConsumer()
      val detector = new ThreadStarvationDetectorImpl(config, consumer.warningConsumer)
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

  private class BlockingWarningConsumer() {
    private val blocker = new Semaphore(0)
    def waitForWarningEvent():Boolean = blocker.tryAcquire(5, TimeUnit.SECONDS)
    def warningConsumer(msg:String):Unit = blocker.release()
  }

  private class BlockingJob(blockTime:FiniteDuration) extends Runnable {
    override def run(): Unit = {
      Thread.sleep(blockTime.toMillis)
    }
  }
  private def createExecutionContext(name:String, threadCount:Int):ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threadCount, NamedThreadFactory(name)))
}