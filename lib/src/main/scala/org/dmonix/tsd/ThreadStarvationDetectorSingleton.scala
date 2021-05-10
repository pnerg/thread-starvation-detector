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

import com.typesafe.config.Config

/**
 * Global singleton for holding a shared instance of the [[ThreadStarvationDetector]].
 * The [[init()]] method is invoked from the 'ServiceRunner' class during initiation of the application.
 * This allows the application to anywhere in its code to use the [[get()]] method to access the global singleton
 *
 * @since 1.0.0
 */
object ThreadStarvationDetectorSingleton {
  private var singleton: Option[ThreadStarvationDetector] = None

  /**
   * Get the global [[ThreadStarvationDetector]] singleton.
   * Will throw an exception if [[init()]] has not been invoked first.
   *
   * @return
   */
  def get(): ThreadStarvationDetector = synchronized {
    singleton.getOrElse(sys.error("Init has not been invoked on this singleton"))
  }

  /**
   * Initialises the global singleton from the provided configuration.
   * Consecutive calls have no effect and the already created singleton is returned
   *
   * @param config
   * @return
   */
  def init(config: Config): ThreadStarvationDetector = synchronized {
    singleton match {
      case Some(detector) => detector
      case None =>
        val detector = ThreadStarvationDetector(config)
        singleton = Some(detector)
        detector
    }
  }
}
