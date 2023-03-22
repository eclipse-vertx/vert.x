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
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.impl.pool.Endpoint;
import io.vertx.core.spi.metrics.ClientMetrics;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class ClientHttpEndpointBase<C> extends Endpoint<C> {

  private final ClientMetrics metrics; // Shall be removed later combining the PoolMetrics with HttpClientMetrics

  ClientHttpEndpointBase(ClientMetrics metrics, Runnable dispose) {
    super(dispose);

    this.metrics = metrics;
  }

  @Override
  public Future<C> requestConnection(ContextInternal ctx, long timeout) {
    Future<C> fut = requestConnection2(ctx, timeout);
    if (metrics != null) {
      Object metric;
      if (metrics != null) {
        metric = metrics.enqueueRequest();
      } else {
        metric = null;
      }
      fut = fut.andThen(ar -> {
        metrics.dequeueRequest(metric);
      });
    }
    return fut;
  }

  protected abstract Future<C> requestConnection2(ContextInternal ctx, long timeout);

  abstract void checkExpired();

  @Override
  protected void dispose() {
    if (metrics != null) {
      metrics.close();
    }
  }
}
