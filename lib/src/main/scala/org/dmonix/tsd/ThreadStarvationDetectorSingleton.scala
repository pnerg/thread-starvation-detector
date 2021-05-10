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
