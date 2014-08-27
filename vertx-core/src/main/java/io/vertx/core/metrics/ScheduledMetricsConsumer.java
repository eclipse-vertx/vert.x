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

package io.vertx.core.metrics;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class ScheduledMetricsConsumer {
  private final Vertx vertx;
  private final BiConsumer<String, JsonObject> consumer;

  private TimeUnit rateUnit = MetricsProvider.DEFAULT_RATE_UNIT;
  private TimeUnit durationUnit = MetricsProvider.DEFAULT_DURATION_UNIT;
  private BiPredicate<String, JsonObject> filter = MetricsProvider.ALL_FILTER;

  private volatile long timerId = -1;

  public ScheduledMetricsConsumer(Vertx vertx, BiConsumer<String, JsonObject> consumer) {
    this.vertx = vertx;
    this.consumer = consumer;
  }

  public ScheduledMetricsConsumer convertRatesTo(TimeUnit rateUnit) {
    if (timerId != -1) throw new IllegalStateException("Cannot set rate unit while metrics consumer is running.");
    this.rateUnit = rateUnit;
    return this;
  }

  public ScheduledMetricsConsumer convertDurationsTo(TimeUnit durationUnit) {
    if (timerId != -1) throw new IllegalStateException("Cannot set duration unit while metrics consumer is running.");
    this.durationUnit = durationUnit;
    return this;
  }

  public ScheduledMetricsConsumer filter(BiPredicate<String, JsonObject> filter) {
    if (timerId != -1) throw new IllegalStateException("Cannot set filter while metrics consumer is running.");
    this.filter = filter;
    return this;
  }

  public void start(long delay, TimeUnit unit) {
    timerId = vertx.setPeriodic(unit.toMillis(delay), tid -> {
      vertx.metricsProvider().getMetrics(rateUnit, durationUnit, filter).forEach(consumer);
    });
  }

  public void stop() {
    vertx.cancelTimer(timerId);
    timerId = -1;
  }
}
