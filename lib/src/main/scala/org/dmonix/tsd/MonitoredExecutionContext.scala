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

private[tsd] class MonitoredExecutionContext(val name:String, val monitorConfig: MonitorConfig, val executionContext: ExecutionContext, val reporters:Seq[Reporter]) extends MonitoredThreadPool {
  /** The state of this monitor.*/
  @volatile private var running = false
  /** How many times this monitor has failed the test. */
  private var failureCounter = 0

  override def failureCount = failureCounter

  /** Mark the monitor as under test. */
  private[tsd] def startTest():Unit = running = true
  /**
   * Mark the monitor NOT under test and report the duration the test job took
   * @param duration
   * @return If a notification should be sent or not due to exceeded queue time
   */
  private[tsd] def finishTest(duration:FiniteDuration):Unit = {
    running = false
    //the reported duration exceeds the configured max time
    if(duration > monitorConfig.maxExecutionTimeThreshold) {
      failureCounter = failureCounter + 1
      reporters.foreach(_.reportFailed(name, duration))
    }
    // successful test
    else {
      reporters.foreach(_.reportSuccessful(name, duration))
    }
  }
  /** If the monitor currently is under test */
  private[tsd] def isRunning:Boolean = running
}

