/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi.metrics;

import io.vertx.core.metrics.Measured;

/**
 * Metrics provider is the base SPI used by metrics implementations to retrieve a {@link io.vertx.core.spi.metrics.Metrics}
 * object.<p/>
 *
 * It is meant to be implemented by {@link io.vertx.core.metrics.Measured} implementations but not exposed directly.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface MetricsProvider extends Measured {

  /**
   * Returns the metrics implementation.
   *
   * @return the metrics
   */
  Metrics getMetrics();

}
