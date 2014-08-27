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

import io.vertx.core.json.JsonObject;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface MetricsProvider {
  static final BiPredicate<String, JsonObject> ALL_FILTER = (name, data) -> true;
  static final TimeUnit DEFAULT_RATE_UNIT = TimeUnit.SECONDS;
  static final TimeUnit DEFAULT_DURATION_UNIT = TimeUnit.MILLISECONDS;

  default Map<String, JsonObject> getMetrics() {
    return getMetrics(DEFAULT_RATE_UNIT, DEFAULT_DURATION_UNIT);
  }

  default Map<String, JsonObject> getMetrics(BiPredicate<String, JsonObject> filter) {
    return getMetrics(DEFAULT_RATE_UNIT, DEFAULT_DURATION_UNIT, filter);
  }

  default Map<String, JsonObject> getMetrics(TimeUnit rateUnit, TimeUnit durationUnit) {
    return getMetrics(rateUnit, durationUnit, ALL_FILTER);
  }

  Map<String, JsonObject> getMetrics(TimeUnit rateUnit, TimeUnit durationUnit, BiPredicate<String, JsonObject> filter);
}
