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
package io.vertx.core.http.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.net.impl.clientconnection.Lease;
import io.vertx.core.net.impl.clientconnection.Pool;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class ClientHttpStreamEndpoint extends ClientHttpEndpointBase<Lease<HttpClientConnection>> {

  private final Pool<HttpClientConnection> pool;

  public ClientHttpStreamEndpoint(ClientMetrics metrics,
                                  Object metric,
                                  int queueMaxSize,
                                  long maxSize,
                                  String host,
                                  int port,
                                  ContextInternal ctx,
                                  HttpConnectionProvider connector,
                                  Runnable dispose) {
    super(metrics, port, host, metric, dispose);
    this.pool = new Pool<>(
      ctx,
      connector,
      queueMaxSize,
      connector.weight(),
      maxSize,
      this::connectionAdded,
      this::connectionRemoved,
      false);
  }

  void checkExpired() {
    pool.closeIdle();
  }

  @Override
  public void requestConnection2(ContextInternal ctx, Handler<AsyncResult<Lease<HttpClientConnection>>> handler) {
    pool.getConnection(handler);
  }

  @Override
  public void close(Lease<HttpClientConnection> lease) {
    lease.get().close();
  }
}
