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
 * Tests for the [[ThreadStarvationDetectorSingleton]] class
 * @author Peter Nerg
 */
class ThreadStarvationDetectorSingletonSpec extends Specification with TestUtils {
  "Using ThreadStarvationDetectorSingleton shall" >> {
    val singleton = ThreadStarvationDetectorSingleton.init(config)
    "return the singleton instance if already initiated" >> {
      ThreadStarvationDetectorSingleton.init(config) === singleton
    }
    "return the singleton for every get" >> {
      ThreadStarvationDetectorSingleton.get() === singleton
    }
  }
}
