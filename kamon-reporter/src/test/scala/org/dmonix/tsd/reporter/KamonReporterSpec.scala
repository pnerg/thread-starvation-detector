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

import com.typesafe.config.ConfigFactory
import org.dmonix.tsd.{Report, ReporterBuilder, ReporterConfig}
import org.specs2.mutable.Specification

import scala.concurrent.duration.DurationInt

/**
 * Tests for the [[KamonReporter]] class
 */
class KamonReporterSpec extends Specification {
  private val config = ConfigFactory.defaultApplication().withFallback(ConfigFactory.defaultReference())
  "factory shall create instance of reporter" >> {
    new KamonReporter.Factory().newReporter(ReporterConfig("kamon-reporter", "", true, config)) must beAnInstanceOf[KamonReporter]
  }
  "creating instance of reporter with ReporterBuilder using default reference conf shall be successful" >> {
    val res = ReporterBuilder.createReporter("kamon-reporter", config)
    res must beSome
    res.get must beAnInstanceOf[KamonReporter]
  }
  "reportSuccessful shall" >> {
    "work for single invocation" >> {
      new KamonReporter().reportSuccessful(Report("test-name", 2.millis, 1, 0, 0))
      ok
    }
    "work for multiple invocations for same monitor" >> {
      val reporter = new KamonReporter()
      reporter.reportSuccessful(Report("test-name", 2.millis, 1, 0, 0))
      reporter.reportSuccessful(Report("test-name", 2.millis, 2, 0, 0))
      ok
    }
    "work for multiple invocations of different monitor" >> {
      val reporter = new KamonReporter()
      reporter.reportSuccessful(Report("test-name", 2.millis, 1, 0, 0))
      reporter.reportSuccessful(Report("test-name-2", 2.millis, 1, 0, 0))
      ok
    }
  }
  "reportFailed shall" >> {
    "work for single invocation" >> {
      new KamonReporter().reportFailed(Report("test-name", 2.millis, 1, 1, 1))
      ok
    }
    "work for multiple invocations for same monitor" >> {
      val reporter = new KamonReporter()
      reporter.reportFailed(Report("test-name", 2.millis, 1, 1, 1))
      reporter.reportFailed(Report("test-name", 2.millis, 2, 2, 2))
      ok
    }
    "work for multiple invocations of different monitor" >> {
      val reporter = new KamonReporter()
      reporter.reportFailed(Report("test-name", 2.millis, 1, 1, 1))
      reporter.reportFailed(Report("test-name-2", 2.millis, 1, 1, 1))
      ok
    }
  }
}
