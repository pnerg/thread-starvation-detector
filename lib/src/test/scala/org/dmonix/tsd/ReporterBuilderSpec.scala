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

/**
 * Tests for the [[ReporterBuilder]] class.
 */
class ReporterBuilderSpec extends Specification with TestUtils {
  "Create instance of reporter shall" >> {
    import ReporterBuilder.createReporter
    "return the configured custom reporter" >> {
      val localConfig = mockReporterConfig(true).withFallback(config)
      createReporter("mock-reporter", localConfig) must beSome
    }
    "return None if the custom reporter is disabled" >> {
      val localConfig = mockReporterConfig(false).withFallback(config)
      createReporter("mock-reporter", localConfig) must beNone
    }
  }
  "Creating list of reporters shall" >> {
    import ReporterBuilder.createReporters
    "yield a empty list if there are no configured reporters" >> {
      createReporters(config) must beEmpty
    }
    "return the added custom/mock logger" >> {
      val localConfig = mockReporterConfig(true).withFallback(config)
      createReporters(localConfig) must have size(1)
    }
    "return a empty list if the custom/mock logger is disabled" >> {
      val localConfig = mockReporterConfig(false).withFallback(config)
      createReporters(localConfig) must beEmpty
    }
  }

}
