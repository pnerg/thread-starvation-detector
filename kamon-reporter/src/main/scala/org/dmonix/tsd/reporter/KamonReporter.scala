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
package org.dmonix.tsd.reporter

import kamon.Kamon
import org.dmonix.tsd.{Report, Reporter, ReporterConfig, ReporterFactory, ThreadStarvationDetector}
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeUnit
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration

object KamonReporter {
  class Factory extends ReporterFactory {
    override def newReporter(reporterConfig: ReporterConfig): Reporter = {
      new KamonReporter()
    }
  }
}

/**
 * Converts test reports to Kamon metrics
 */
class KamonReporter extends Reporter {
  private val metricsMap = new TrieMap[String, ExecutorMetrics]()
  override def reportSuccessful(report:Report): Unit = getExecutorMetrics(report.name).reportSuccessful(report.duration)

  override def reportFailed(report:Report): Unit = getExecutorMetrics(report.name).reportFailed(report.duration)

  private def getExecutorMetrics(name:String):ExecutorMetrics = metricsMap.getOrElseUpdate(name, new ExecutorMetrics(name))

  /**
   * Holds references to all metrics for a single monitored execution context
   * @param name
   */
  private class ExecutorMetrics(name:String) {
    private val successiveFailures = Kamon.gauge("thread_starvation_detector_successive_failures", "Shows successive failures for the named execution context, is reset on first successful execution").withTag("name", name)
    private val testCounter = Kamon.counter("thread_starvation_detector_executions", "Counts the test executions towards the named execution context").withTag("name", name)
    private val successCounter = testCounter.withTag("success", "true")
    private val failureCounter = testCounter.withTag("success", "false")
    private val timer = Kamon.timer("thread_starvation_detector_queue_time", "Measures the queue time for executing a test job in the named execution context").withTag("name", name)

    def reportSuccessful(duration: FiniteDuration): Unit = {
      updateCounter(duration)
      successiveFailures.update(0) //resets the failure gauge
      successCounter.increment()
    }

    def reportFailed(duration: FiniteDuration): Unit = {
      updateCounter(duration)
      failureCounter.increment()
      successiveFailures.increment()
    }

    private def updateCounter(duration: FiniteDuration):Unit = {
      testCounter.increment()
      timer.record(duration.toMillis, TimeUnit.MILLISECONDS)
    }
  }
}
