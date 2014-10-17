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
  private final Measured measured;

  private BiPredicate<String, JsonObject> filter = (name, metric) -> true;

  private volatile long timerId = -1;

  public ScheduledMetricsConsumer(Vertx vertx) {
    this(vertx, vertx);
  }

  public ScheduledMetricsConsumer(Vertx vertx, Measured measured) {
    this.vertx = vertx;
    this.measured = measured;
  }

  public ScheduledMetricsConsumer filter(BiPredicate<String, JsonObject> filter) {
    if (timerId != -1) throw new IllegalStateException("Cannot set filter while metrics consumer is running.");
    this.filter = filter;
    return this;
  }

  public void start(long delay, TimeUnit unit, BiConsumer<String, JsonObject> consumer) {
    timerId = vertx.setPeriodic(unit.toMillis(delay), tid -> {
      measured.metrics().forEach((name, metric) -> {
        if (filter.test(name, metric)) {
          consumer.accept(name, metric);
        }
      });
    });
  }

  public void stop() {
    vertx.cancelTimer(timerId);
    timerId = -1;
  }
}
