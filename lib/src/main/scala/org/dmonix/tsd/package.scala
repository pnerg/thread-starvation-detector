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
package org.dmonix

import com.typesafe.config.Config

import scala.concurrent.duration.{Duration, FiniteDuration}

package object tsd {
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

  private[tsd] case class MonitorConfig(maxExecutionTimeThreshold: FiniteDuration, warningSilenceDuration: FiniteDuration, loggingEnabled: Boolean)

  private[tsd] case class ThreadStarvationDetectorConfig(initialDelay: FiniteDuration, checkInterval: FiniteDuration, defaultMonitorConfig: MonitorConfig, customMonitorConfig: Map[String, MonitorConfig]) {
    /**
     * Returns the custom config for the named execution context, if not found the [[defaultMonitorConfig]] is returned
     *
     * @param name The name as provided when registering the execution context
     * @return
     */
    private[tsd] def getCustomOrDefaultMonitorConfig(name: String): MonitorConfig = customMonitorConfig.get(name) getOrElse defaultMonitorConfig
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
