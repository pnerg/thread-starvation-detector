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

import com.typesafe.config.{Config, ConfigValue}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}
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

  /**
   * The total amount of times this execution context has failed its monitoring criteria
   * @return
   */
  def failureCount:Int
}

/**
 * Companion object to create [[ThreadStarvationDetector]] instances
 * @since 1.0
 */
object ThreadStarvationDetector {
  private val logger = LoggerFactory.getLogger(classOf[ThreadStarvationDetector])
  private[tsd] type WarningConsumer = String => Unit

  private implicit class PimpedConfig(config: Config) {
    def getFiniteDuration(path: String): FiniteDuration = Duration.fromNanos(config.getDuration(path).toNanos)

    def getFiniteDurationOpt(path: String): Option[FiniteDuration] = {
      if (config.hasPath(path))
        Option(getFiniteDuration(path))
      else
        None
    }

    def getBooleanOpt(path: String): Option[Boolean] = {
      if (config.hasPath(path))
        Option(config.getBoolean(path))
      else
        None
    }
  }

  /**
   * Creates the detector instance.
   */
  def apply(config: Config): ThreadStarvationDetector = {
    //monitoring is enabled
    if (config.getBoolean("thread-starvation-detector.enabled")) {
      new ThreadStarvationDetectorImpl(parseConfig(config), createReporters(config))
    }
    //if monitoring is disabled then we use the no-op detector
    else {
      new NoOpThreadStarvationDetector()
    }
  }

  private[tsd] def createReporters(config: Config):Seq[Reporter] = {
    import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
    config.getConfig("thread-starvation-detector.reporter").root().keySet().map(name => createReporter(name, config.getConfig(s"thread-starvation-detector.reporter.$name"), config)).flatten.toSeq
  }

  private[tsd] def createReporter(name:String, config: Config, fullConfig:Config):Option[Reporter] = {
    try {
      val reporterConfig = ReporterConfig(name,
        config.getString("description"),
        config.getBoolean("enabled"),
        fullConfig
      )
      if (reporterConfig.enabled) {
        val factory = Class.forName(config.getString("factory")).getDeclaredConstructor().newInstance().asInstanceOf[ReporterFactory]
        Option(factory.newReporter(reporterConfig))
      } else {
        None
      }
    } catch{
      case ex =>
        logger.error(s"Failed to create instance of reporter [$name] due to [${ex.getMessage}]")
        None
    }

  }

  /**
   * Parses the config to case class representations
   *
   * @param config
   * @return
   */
  private[tsd] def parseConfig(config: Config): ThreadStarvationDetectorConfig = {
    import scala.collection.JavaConverters._

    val detectorConfig = config.getConfig("thread-starvation-detector")
    val defaultMonitorCfg = MonitorConfig(
      maxExecutionTimeThreshold = detectorConfig.getFiniteDuration("max-execution-time-threshold"),
      warningSilenceDuration = detectorConfig.getFiniteDuration("warning-silence-duration"),
      loggingEnabled = detectorConfig.getBoolean("logging-enabled")
    )

    //read the thread-starvation-detector.custom config section and pick any custom values
    val customMonitorConfig = detectorConfig.getObject("custom").keySet().asScala.map { name =>
      val customConfig = detectorConfig.getConfig("custom." + name)
      val cfg = MonitorConfig(
        maxExecutionTimeThreshold = customConfig.getFiniteDurationOpt("max-execution-time-threshold") getOrElse defaultMonitorCfg.maxExecutionTimeThreshold,
        warningSilenceDuration = customConfig.getFiniteDurationOpt("warning-silence-duration") getOrElse defaultMonitorCfg.warningSilenceDuration,
        loggingEnabled = customConfig.getBooleanOpt("logging-enabled") getOrElse defaultMonitorCfg.loggingEnabled
      )
      (name, cfg)
    }.toMap

    ThreadStarvationDetectorConfig(
      initialDelay = detectorConfig.getFiniteDuration("initial-delay"),
      checkInterval = detectorConfig.getFiniteDuration("check-interval"),
      defaultMonitorConfig = defaultMonitorCfg,
      customMonitorConfig = customMonitorConfig
    )
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
        val me = new MonitoredExecutionContext(name, monitorConfig, ec, reporters)
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
    override def run(): Unit = mo.finishTest(Duration.fromNanos(System.nanoTime - startTime))
  }
}

/**
 * A no-op detector used when the feature is disabled from start/config
 * @since 1.0
 */
private[tsd] class NoOpThreadStarvationDetector extends ThreadStarvationDetector {
  private object NoOpMonitoredThreadPool extends MonitoredThreadPool {
    cancel()
    override def failureCount: Int = 0
  }
  override def monitorExecutionContext(name:String, ec: ExecutionContext): MonitoredThreadPool = NoOpMonitoredThreadPool
  override def stop(): Unit = {}
}