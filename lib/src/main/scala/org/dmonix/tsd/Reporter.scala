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
