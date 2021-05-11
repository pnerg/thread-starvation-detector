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

import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration

object LoggingReporter {
  class Factory extends ReporterFactory {
    override def newReporter(reporterConfig: ReporterConfig): Reporter = new LoggingReporter()
  }
}

class LoggingReporter extends Reporter {
  private val logger = LoggerFactory.getLogger(classOf[LoggingReporter])
  /**
   * Report a successful execution of a named execution context.
   *
   * @param name     The name of the execution context
   * @param duration The duration for the test
   */
  override def reportSuccessful(name: String, duration: FiniteDuration): Unit = {
    logger.debug(s"Successfully executed a test job in [$name] with a duration of [${duration.toMillis}]ms")
  }

  /**
   * Report a failed execution of a named execution context.
   *
   * @param name     The name of the execution context
   * @param duration The duration for the test
   */
  override def reportFailed(name: String, duration: FiniteDuration): Unit = ???

  override def stop(): Unit = {}

  private class Logxxx(name:String, warningSilenceDuration: FiniteDuration) {
    /** The EPOCH time since we last sent a failure notification. Not last failure but when it was notified last time. */
    @volatile private var lastFailureNotificationTime = 0L
    private def logFailureIfAllowed(duration: FiniteDuration):Unit = {
      //are we allowed to log the event, i.e. was the last error within the silence period?
      val now = System.currentTimeMillis()
      if(now-lastFailureNotificationTime > warningSilenceDuration.toMillis) {
        lastFailureNotificationTime = now
        //logger.error(s"Executing a test job in [${name}] took [${duration.toMillis}]ms, max configured threshold is [${mo.monitorConfig.maxExecutionTimeThreshold.toMillis}]ms, total times this pool has failed checks is [${mo.failureCount}]. Possible cause is CPU and/or thread starvation")
      }
    }
  }
}