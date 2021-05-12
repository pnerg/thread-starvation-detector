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
import org.dmonix.tsd.ThreadStarvationDetector.logger
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration

case class ReporterConfig(name:String, description:String, enabled:Boolean, config:Config)

/**
 *
 * @param name The name of the execution context
 * @param duration The duration for the test
 */
case class Report(name:String, duration:FiniteDuration, totalTestCount:Int, totalFailureCount:Int, successiveFailureCount:Int)

object ReporterBuilder {
  private val logger = LoggerFactory.getLogger(ReporterBuilder.getClass)
  private[tsd] def createReporters(config: Config):Seq[Reporter] = {
    import scala.collection.JavaConverters._

    config.getConfig("thread-starvation-detector.reporter")
      .root()
      .keySet()
      .asScala
      .map(name => createReporter(name, config))
      .flatten.toSeq
  }

  private[tsd] def createReporter(name:String, config:Config):Option[Reporter] = {
    val localCfg = config.getConfig(s"thread-starvation-detector.reporter.$name")
    try {
      val reporterConfig = ReporterConfig(name,
        localCfg.getString("description"),
        localCfg.getBoolean("enabled"),
        config
      )
      if (reporterConfig.enabled) {
        logger.info(s"Starting [$name] reporter")
        val factory = Class.forName(localCfg.getString("factory")).getDeclaredConstructor().newInstance().asInstanceOf[ReporterFactory]
        Option(factory.newReporter(reporterConfig))
      } else {
        logger.debug(s"Reporter [$name] is disabled, ignoring it")
        None
      }
    } catch{
      case ex:Throwable =>
        logger.error(s"Failed to create instance of reporter [$name] due to [${ex.getMessage}]")
        None
    }
  }
}

/**
 * Factory for creating [[Reporter]] instances.
 * The implementation must have a default/no arg constructor.
 * @since 1.0
 */
trait ReporterFactory {
  def newReporter(reporterConfig: ReporterConfig):Reporter
}

/**
 * Receiver of reports from test executions.
 * @since 1.0
 */
trait Reporter {
  /**
   * Report a successful execution of a named execution context.
   * @param report The test report
   */
  def reportSuccessful(report: Report)

  /**
   * Report a failed execution of a named execution context.
   * @param report The test report
   */
  def reportFailed(report:Report)

  /**
   * Invoked when the ''ThreadStarvationDetector.stop'' is invoked
   * Optional method, default implementation does nothing.
   */
  def stop():Unit = {}
}
