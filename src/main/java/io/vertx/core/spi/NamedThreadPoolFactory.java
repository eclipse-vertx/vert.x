/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.spi;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.Map;

/**
 * A SPI that allows defining named thread pools. This SPI does not create the thread pools, just provide the
 * configuration (name and number of threads).
 * <p>
 * The thread pools are created by vert.x and are fixed size thread pools.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public interface NamedThreadPoolFactory {

  /**
   * Configures the SPI. This method lets the SPI implementation to read data from the system or from the configuration.
   *
   * @param vertx  the vert.x instance
   * @param config the configuration
   */
  void configure(Vertx vertx, JsonObject config);

  /**
   * @return the thread pool configuration, must not be {@code null}. Each entry is a thread pool named with the key
   * and the size is the value
   */
  Map<String, Integer> getNamedThreadPools();
}
