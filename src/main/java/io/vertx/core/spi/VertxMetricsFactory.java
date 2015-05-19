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

package io.vertx.core.spi;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.metrics.VertxMetrics;

/**
 * A factory for the plugable metrics SPI.
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface VertxMetricsFactory {

  /**
   * Create a new {@link io.vertx.core.spi.metrics.VertxMetrics} object.<p/>
   *
   * No specific thread and context can be expected when this method is called.
   *
   * @param vertx the vertx instance
   * @param options the metrics configuration option
   * @return the metrics implementation
   */
  VertxMetrics metrics(Vertx vertx, VertxOptions options);
}
