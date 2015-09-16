/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
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
