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

import com.typesafe.config.{Config, ConfigFactory}
import org.specs2.control.NamedThreadFactory

import java.util.concurrent.{Executors, Semaphore, TimeUnit}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

trait TestUtils {
  /** The default reference config */
  lazy val config:Config = ConfigFactory.defaultReference()
  /** Creates a new config with the [[config]] as fallback */
  def config(cfg:String):Config = ConfigFactory.parseString(cfg).withFallback(config)

  def mockReporterConfig(enabled:Boolean):Config = {
    ConfigFactory.parseString(
      s"""
         |thread-starvation-detector.reporter {
         |  mock-reporter {
         |    enabled = $enabled
         |    name = "Kamon Metrics Reporter"
         |    description = "Generates Kamon metrics for monitored execution contexts"
         |    factory = "org.dmonix.tsd.MockReporterFactory"
         |  }
         |}
         |""".stripMargin
    )
  }

  /**
   * Creates a execution context
   * @param name
   * @param threadCount
   * @return
   */
  def createExecutionContext(name:String, threadCount:Int):ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threadCount, NamedThreadFactory(name)))

}
