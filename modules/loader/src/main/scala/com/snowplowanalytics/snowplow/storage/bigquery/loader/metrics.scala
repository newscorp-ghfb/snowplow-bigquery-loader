/*
 * Copyright (c) 2018-2021 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.storage.bigquery.loader

import com.snowplowanalytics.snowplow.storage.bigquery.common.config.CliConfig.Environment.LoaderEnvironment
import com.snowplowanalytics.snowplow.storage.bigquery.common.config.model.Monitoring.Dropwizard

import cats.syntax.either._
import com.codahale.metrics.{Gauge, MetricRegistry, Slf4jReporter}
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeUnit

object metrics {
  private val metrics = new MetricRegistry()
  private val logger  = LoggerFactory.getLogger("bigquery.loader.metrics")
  val reporter        = Slf4jReporter.forRegistry(metrics).outputTo(logger).build()
  val latency         = new MetricRegistryOps(metrics)

  // Take the latest value every 'period' second.
  def startReporter(reporter: Slf4jReporter, env: LoaderEnvironment): Unit = env.monitoring.dropwizard match {
    case Some(Dropwizard(period)) => reporter.start(period.toSeconds, TimeUnit.SECONDS)
    case _                        => ()
  }

  /** Operations performed on a `MetricRegistry` */
  final class MetricRegistryOps(registry: MetricRegistry) {
    // A by-name param:
    // - lets us use f directly
    // - ensures we calculate f at the last possible moment
    // - makes it easier to change our mind about f's input
    def gauge[T](name: String, f: => T): Gauge[T] = {
      // GaugeWithValue registers a fresh Gauge metric every time, so first we have to clean up the registry.
      registry.remove(name)
      registry.register(name, GaugeWithValue(f))
    }

    // `getValue` can throw an exception.
    def update(diff: Long): Either[Throwable, Long] =
      Either.catchNonFatal(this.gauge("bigquery.loader.latency", diff).getValue)
  }

  /** The default `Gauge` has no way to pass in a value. */
  final object GaugeWithValue {
    def apply[T](f: => T): Gauge[T] = new Gauge[T] { def getValue: T = f }
  }
}
