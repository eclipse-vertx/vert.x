/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.metrics.impl;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.metrics.impl.reporters.EventBusMetricReporter;
import io.vertx.core.metrics.impl.reporters.codahale.JmxReporter;
import io.vertx.core.metrics.spi.Metrics;
import io.vertx.core.spi.MetricsFactory;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class MetricsFactoryImpl implements MetricsFactory {
  @Override
  public Metrics metrics(Vertx vertx, VertxOptions options) {
    MetricsImpl metrics = new MetricsImpl(vertx, options);
    startReporters(metrics, vertx, options);

    return metrics;
  }

  private static void startReporters(MetricsImpl metrics, Vertx vertx, VertxOptions options) {
    if (options.isMetricsEnabled()) {
      // Start jmx reporter if jmx is enabled
      if (options.isJmxEnabled()) {
        String jmxDomain = options.getJmxDomain();
        if (jmxDomain == null) {
          jmxDomain = "vertx" + "@" + Integer.toHexString(vertx.hashCode());
        }
        JmxReporter.forRegistry(metrics.registry()).inDomain(jmxDomain).build().start();
      } else {
        // Start event bus if metrics is enabled & jmx is not
        //TODO: Allow configuration of duration
        new EventBusMetricReporter(vertx, metrics.registry()).start(5, TimeUnit.MINUTES);
      }
    }
  }
}
