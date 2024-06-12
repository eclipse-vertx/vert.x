/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi;

import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.json.Json;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Entry point for loading Vert.x SPI implementations.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface VertxServiceProvider {

  /**
   * Let the provider initialize the Vert.x builder.
   *
   * @param builder the builder
   */
  void init(VertxBuilder builder);

}
