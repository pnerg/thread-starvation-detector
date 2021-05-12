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

import java.util.concurrent.{Semaphore, TimeUnit}

class MockReporterFactory extends ReporterFactory {
  override def newReporter(reporterConfig: ReporterConfig): Reporter = MockReporter()
}
object MockReporter {
  def apply():MockReporter = new MockReporter()
}
class MockReporter extends Reporter {
  var report:Option[(Boolean, Report)] = None
  val semaphore = new Semaphore(0)

  override def reportSuccessful(report: Report): Unit = reportAndRelease(true, report)
  override def reportFailed(report: Report): Unit = reportAndRelease(false, report)

  def waitForReport():Boolean = semaphore.tryAcquire(5, TimeUnit.SECONDS)

  private def reportAndRelease(result:Boolean, r:Report):Unit = {
    report = Some((result, r))
    semaphore.release()
  }
}
