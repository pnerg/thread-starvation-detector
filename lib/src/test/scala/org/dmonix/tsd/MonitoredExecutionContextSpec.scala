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
 * Test for the [[MonitoredExecutionContext]] class.
 */
class MonitoredExecutionContextSpec extends Specification with TestUtils {

  private def monitoredExecutionContext(maxExecutionTimeThreshold:FiniteDuration, warningSilenceDuration:FiniteDuration):MonitoredExecutionContext = new MonitoredExecutionContext("Test", sameThreadExecutionContext(), maxExecutionTimeThreshold, warningSilenceDuration)

  "A fresh instance shall" >> {
    val me = monitoredExecutionContext(5.seconds, 5.seconds)
    "have zero test count" >> {
      me.testCount === 0
    }
    "have zero failure count" >> {
      me.failureCount === 0
    }
    "have zero consecutive failure count" >> {
      me.consecutiveFailureCount === 0
    }
    "have running state as false" >> {
      me.isRunning === false
    }
  }

  "A instance that has executed once and succeeded" >> {
    val me = monitoredExecutionContext(5.millis, 5.seconds)
    me.finishTest(2.millis) //within the allowed duration
    "have one test count" >> {
      me.testCount === 1
    }
    "have one failure count" >> {
      me.failureCount === 0
    }
    "have one consecutive failure count" >> {
      me.consecutiveFailureCount === 0
    }
  }

  "A instance that has executed once and failed" >> {
    val me = monitoredExecutionContext(5.millis, 5.seconds)
    me.finishTest(10.millis) //more than the allowed duration
    "have one test count" >> {
      me.testCount === 1
    }
    "have one failure count" >> {
      me.failureCount === 1
    }
    "have one consecutive failure count" >> {
      me.consecutiveFailureCount === 1
    }
  }

  "A instance that has executed twice, first failed and then succeeded" >> {
    val me = monitoredExecutionContext(5.millis, 5.seconds)
    me.finishTest(10.millis) //more than the allowed duration
    me.finishTest(2.millis) //within the allowed duration
    "have two test count" >> {
      me.testCount === 2
    }
    "have one failure count" >> {
      me.failureCount === 1
    }
    "have zero consecutive failure count" >> {
      me.consecutiveFailureCount === 0
    }
  }

  "A instance that has executed twice, first succeeded and then failed" >> {
    val me = monitoredExecutionContext(5.millis, 5.seconds)
    me.finishTest(2.millis) //within the allowed duration
    me.finishTest(10.millis) //more than the allowed duration
    "have two test count" >> {
      me.testCount === 2
    }
    "have one failure count" >> {
      me.failureCount === 1
    }
    "have one consecutive failure count" >> {
      me.consecutiveFailureCount === 1
    }
  }

  "A instance that has executed twice, both failed" >> {
    val me = monitoredExecutionContext(5.millis, 5.seconds)
    me.finishTest(10.millis) //more than the allowed duration
    me.finishTest(10.millis) //more than the allowed duration
    "have two test count" >> {
      me.testCount === 2
    }
    "have two failure count" >> {
      me.failureCount === 2
    }
    "have two consecutive failure count" >> {
      me.consecutiveFailureCount === 2
    }
  }

  "A instance that has executed twice, both successful" >> {
    val me = monitoredExecutionContext(5.millis, 5.seconds)
    me.finishTest(2.millis) //within the allowed duration
    me.finishTest(2.millis) //within the allowed duration
    "have two test count" >> {
      me.testCount === 2
    }
    "have zero failure count" >> {
      me.failureCount === 0
    }
    "have zero consecutive failure count" >> {
      me.consecutiveFailureCount === 0
    }
  }

  "When finishing a test it shall" >> {
    "be returned as failed in case over allowed duration" >> {
      val me = monitoredExecutionContext(5.millis, 5.seconds)
      val (result, _, _) = me.finishTest(10.millis) //more than the allowed duration
      result === false
    }
    "be returned as success in case within allowed duration" >> {
      val me = monitoredExecutionContext(5.millis, 5.seconds)
      val (result, _, _) = me.finishTest(2.millis) //within the allowed duration
      result === true
    }
    "allow logging the first failure" >> {
      val me = monitoredExecutionContext(5.millis, 5.seconds)
      val (result, log, _) = me.finishTest(10.millis) //more than the allowed duration
      result === false
      log === true
    }
    "shall not allow logging if the second test was within the warning silence duration" >> {
      val me = monitoredExecutionContext(5.millis, 5.seconds)
      val (result, log, _) = me.finishTest(10.millis) //more than the allowed duration
      result === false
      log === true

      //still failure but not allowed to log
      val (result2, log2, _) = me.finishTest(10.millis) //more than the allowed duration
      result2 === false
      log2 === false
    }

    "shall allow logging if the second test was not within the warning silence duration" >> {
      val me = monitoredExecutionContext(5.millis, 1.millis) //very short 'warningSilenceDuration'
      val (result, log, _) = me.finishTest(10.millis) //more than the allowed duration
      result === false
      log === true

      Thread.sleep(2) //add a short break

      //still failure and should be allowed to log as enough time has been between the tests
      val (result2, log2, _) = me.finishTest(10.millis) //more than the allowed duration
      result2 === false
      log2 === true
    }
  }

  "When starting a test running state is set to true" >> {
    val me = monitoredExecutionContext(5.millis, 5.seconds)
    me.startTest()
    me.isRunning === true
  }

}
