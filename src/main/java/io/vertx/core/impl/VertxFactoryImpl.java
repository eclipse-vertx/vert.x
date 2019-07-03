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

import io.vertx.core.*;
import io.vertx.core.net.impl.transport.Transport;
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
    return vertx(options, Transport.transport(options.getPreferNativeTransport()));
  }

  @Override
  public Vertx vertx(VertxOptions options, Transport transport) {
    if (options.getEventBusOptions().isClustered()) {
      throw new IllegalArgumentException("Please use Vertx.clusteredVertx() to create a clustered Vert.x instance");
    }
    return VertxImpl.vertx(options, transport);
  }

  @Override
  public void clusteredVertx(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler) {
    clusteredVertx(options, Transport.transport(options.getPreferNativeTransport()), resultHandler);
  }

  @Override
  public void clusteredVertx(VertxOptions options, Transport transport, Handler<AsyncResult<Vertx>> resultHandler) {
    // We don't require the user to set clustered to true if they use this method
    options.getEventBusOptions().setClustered(true);
    VertxImpl.clusteredVertx(options, transport, resultHandler);
  }

  @Override
  public Context context() {
    return AbstractContext.context();
  }
}
