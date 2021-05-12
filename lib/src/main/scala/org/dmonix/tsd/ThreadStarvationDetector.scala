/**
 *  Copyright 2021 Peter Nerg
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

import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.util.Try

/**
 * Allows for monitoring of thread starvation in thread pools
 * @since 1.0
 */
trait ThreadStarvationDetector {
  /**
   * Adds an execution context to be monitored
   * @param name Name of the context to be monitored. Used in logging and metrics
   * @param ec The execution context to monitor
   * @return
   */
  def monitorExecutionContext(name:String, ec:ExecutionContext):MonitoredThreadPool

  /**
   * Permanently stops the detector
   */
  def stop():Unit
}

/**
 * Represents a single monitored execution context
 * @since 1.0
 */
trait MonitoredThreadPool {
  @volatile private var cancelled:Boolean = false

  /**
   * Permanently cancels further monitoring of the execution context.
   */
  def cancel():Unit = cancelled = true

  /**
   * If [[cancel()]] has been invoked
   * @return
   */
  def isCancelled:Boolean = cancelled

  /** How many tests this monitor has executed. */
  def testCount:Int

  /** How many times this monitor has failed the test. */
  def failureCount:Int

  /** How many consecutive failures this monitor has, reset on success */
  def consecutiveFailureCount:Int

}

/**
 * Companion object to create [[ThreadStarvationDetector]] instances
 * @since 1.0
 */
object ThreadStarvationDetector {
  private val logger = LoggerFactory.getLogger(classOf[ThreadStarvationDetector])
  private[tsd] type WarningConsumer = String => Unit

  /**
   * Creates the detector instance.
   */
  def apply(config: Config): ThreadStarvationDetector = {
    //monitoring is enabled
    if (config.getBoolean("thread-starvation-detector.enabled")) {
      new ThreadStarvationDetectorImpl(parseConfig(config), ReporterBuilder.createReporters(config))
    }
    //if monitoring is disabled then we use the no-op detector
    else {
      new NoOpThreadStarvationDetector()
    }
  }

}

/**
 * The class for running test jobs against execution contexts
 * @param config The configuration
 * @author Peter Nerg
 * @since 1.0
 */
private[tsd] class ThreadStarvationDetectorImpl(config: ThreadStarvationDetectorConfig, reporters:Seq[Reporter]) extends ThreadStarvationDetector {
  private val logger = LoggerFactory.getLogger(classOf[ThreadStarvationDetector])
  /** Flag for the state of this class. */
  @volatile private var running = true

  /** The queue contains all objects to be tested, each item has an individual state */
  private val queue = mutable.ListBuffer[MonitoredExecutionContext]()

  //start the thread that runs the main monitor runner
  new Thread(new MonitorRunner(), "Thread-Starvation-Detector-Thread").start()

  override def monitorExecutionContext(name:String, ec:ExecutionContext):MonitoredThreadPool = {
    queue.find(_.name == name) match {
      case Some(me) =>
        logger.info(s"Execution context named [$name] already under monitoring, not adding another")
        me
      case None =>
        logger.info(s"Added execution context named [$name] to be monitored")
        val monitorConfig = config.getCustomOrDefaultMonitorConfig(name)
        val me = new MonitoredExecutionContext(name, ec, monitorConfig.maxExecutionTimeThreshold, monitorConfig.warningSilenceDuration)
        queue += me
        me
    }
  }

  override def stop(): Unit = {
    //mark all monitors as cancelled
    queue.foreach(_.cancel())
    running = false
  }

  /**
   * The "main" runner that periodically scans all configured execution contexts.
   * It simply runs until [[stop()]] is invoked executing a test job on every context then sleeping the configured interval
   */
  private class MonitorRunner extends Runnable {
    override def run(): Unit = {
      //initial pause before starting the actual testing
      Thread.sleep(config.initialDelay.toMillis)
      while (running) {
        Try {
          //execute tests on those that have not been cancelled nor already under testing
          queue.filterNot(_.isCancelled).filterNot(_.isRunning).foreach { mo =>
            mo.startTest()
            mo.executionContext.execute(new TestPayload(mo))
          }
          Thread.sleep(config.checkInterval.toMillis)
        }
      }
    }
  }

  /**
   * The test job to run on each executor.
   * Measures the time between having been created and when the job actually got executed.
   * @param mo
   */
  private class TestPayload(mo:MonitoredExecutionContext) extends Runnable {
    //this is the time when the payload was created
    private val startTime = System.nanoTime()

    /**
     * Once this test job gets to execute we just stop the timer.
     * It's the queue time that indicates how busy the executor is.
     */
    override def run(): Unit = {
      val (success, log, report) = mo.finishTest(Duration.fromNanos(System.nanoTime - startTime))
      if(success) {
        logger.debug(s"Successfully executed a test job in [${report.name}] with a duration of [${report.duration.toMillis}]ms")
        reporters.foreach(_.reportSuccessful(report))
      } else {
        if(log)
          logger.error(s"Executing a test job in [${report.name}] took [${report.duration.toMillis}]ms, max configured threshold is [${mo.maxExecutionTimeThreshold.toMillis}]ms, the pool has [${report.successiveFailureCount}] consecutive failures and total failure count of [${report.totalFailureCount}]. Possible cause is CPU and/or thread starvation")
        reporters.foreach(_.reportFailed(report))
      }
    }
  }
}

/**
 * A no-op detector used when the feature is disabled from start/config
 * @since 1.0
 */
private[tsd] class NoOpThreadStarvationDetector extends ThreadStarvationDetector {
  private object NoOpMonitoredThreadPool extends MonitoredThreadPool {
    cancel()
    override def testCount: Int = 0
    override def failureCount: Int = 0
    override def consecutiveFailureCount: Int = 0
  }
  override def monitorExecutionContext(name:String, ec: ExecutionContext): MonitoredThreadPool = NoOpMonitoredThreadPool
  override def stop(): Unit = {}
}