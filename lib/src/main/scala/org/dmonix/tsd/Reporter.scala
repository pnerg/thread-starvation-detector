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

import scala.concurrent.duration.FiniteDuration

case class ReporterConfig(name:String, description:String, enabled:Boolean, config:Config)

trait ReporterFactory {
  def newReporter(reporterConfig: ReporterConfig):Reporter
}

trait Reporter {
  def reportSuccessful(name:String, duration:FiniteDuration)
  def reportFailed(name:String, duration:FiniteDuration)
}
