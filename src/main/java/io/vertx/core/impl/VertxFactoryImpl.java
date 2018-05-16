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

package io.vertx.core.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.VertxFactory;

/**
 * @author pidster
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public class VertxFactoryImpl implements VertxFactory {

  @Override
  public Vertx vertx() {
    return vertx(new VertxOptions());
  }

  @Override
  public Vertx vertx(VertxOptions options) {
    if (options.isClustered()) {
      throw new IllegalArgumentException("Please use Vertx.clusteredVertx() to create a clustered Vert.x instance");
    }
    return VertxImpl.vertx(options);
  }

  @Override
  public void clusteredVertx(VertxOptions options, final Handler<AsyncResult<Vertx>> resultHandler) {
    // We don't require the user to set clustered to true if they use this method
    options.setClustered(true);
    VertxImpl.clusteredVertx(options, resultHandler);
  }

  @Override
  public Context context() {
    return VertxImpl.context();
  }
}
