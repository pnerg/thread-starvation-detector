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

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

/**
 * Represents a single [[ExecutionContext]] that is being monitored.
 * @param name The name provided to the execution context
 * @param executionContext The execution context to monitor
 * @param maxExecutionTimeThreshold The max threshold allowed to
 */
private[tsd] class MonitoredExecutionContext(val name:String, val executionContext:ExecutionContext, val maxExecutionTimeThreshold:FiniteDuration, warningSilenceDuration:FiniteDuration) extends MonitoredThreadPool {
  /** The EPOCH time since we last sent a failure notification. Not last failure but when it was notified last time. */
  @volatile private var lastFailureNotificationTime = 0L

  /** The state of this monitor.*/
  @volatile private var running = false
  /** How many tests this monitor has executed. */
  private var testCounter = 0
  /** How many times this monitor has failed the test. */
  private var failureCounter = 0
  /** How many consecutive failures this monitor has, reset on success */
  private var consecutiveFailureCounter = 0

  override def testCount: Int = testCounter
  override def consecutiveFailureCount: Int = consecutiveFailureCounter
  override def failureCount = failureCounter

  /** Mark the monitor as under test. */
  private[tsd] def startTest():Unit = running = true
  /**
   * Mark the monitor NOT under test and report the duration the test job took
   * @param duration
   * @return If a notification should be sent or not due to exceeded queue time
   */
  private[tsd] def finishTest(duration:FiniteDuration):(Boolean, Boolean, Report) = {
    running = false
    testCounter = testCounter + 1
    //the reported duration exceeds the configured max time
    if(duration > maxExecutionTimeThreshold) {
      failureCounter = failureCounter + 1
      consecutiveFailureCounter = consecutiveFailureCounter + 1
      //are we allowed to log the event, i.e. was the last error within the silence period?
      val now = System.currentTimeMillis()
      //decide if we're allowed to log this failure
      val log = if(now-lastFailureNotificationTime > warningSilenceDuration.toMillis) {
        lastFailureNotificationTime = now
        true
      } else false

      (false, log, Report(name, duration, testCounter, failureCounter, consecutiveFailureCounter))
    }
    // successful test
    else {
      consecutiveFailureCounter = 0
      (true, false, Report(name, duration, testCounter, failureCounter, consecutiveFailureCounter))
    }
  }

  /** If the monitor currently is under test */
  private[tsd] def isRunning:Boolean = running

}

