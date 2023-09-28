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

import io.vertx.core.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.metrics.ClientMetrics;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Set of connections (not pooled) to a host used for WebSocket.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class WebSocketEndpoint extends ClientHttpEndpointBase<HttpClientConnection> {

  private static class Waiter {

    final Promise<HttpClientConnection> promise;
    final ContextInternal context;

    Waiter(ContextInternal context) {
      this.promise = context.promise();
      this.context = context;
    }
  }

  private final int maxPoolSize;
  private final HttpChannelConnector connector;
  private final Deque<Waiter> waiters;
  private int inflightConnections;

  WebSocketEndpoint(ClientMetrics metrics, int maxPoolSize, HttpChannelConnector connector, Runnable dispose) {
    super(metrics, dispose);
    this.maxPoolSize = maxPoolSize;
    this.connector = connector;
    this.waiters = new ArrayDeque<>();
  }

  private void onEvict() {
    decRefCount();
    Waiter h;
    synchronized (WebSocketEndpoint.this) {
      if (--inflightConnections > maxPoolSize || waiters.isEmpty()) {
        return;
      }
      h = waiters.poll();
    }
    tryConnect(h.context).onComplete(h.promise);
  }

  private Future<HttpClientConnection> tryConnect(ContextInternal ctx) {
    ContextInternal eventLoopContext;
    if (ctx.isEventLoopContext()) {
      eventLoopContext = ctx;
    } else {
      eventLoopContext = ctx.owner().createEventLoopContext(ctx.nettyEventLoop(), ctx.workerPool(), ctx.classLoader());
    }
    Future<HttpClientConnection> fut = connector.httpConnect(eventLoopContext);
    return fut.map(c -> {
      if (incRefCount()) {
        c.evictionHandler(v -> onEvict());
        return c;
      } else {
        c.close();
        throw new VertxException("Connection closed", true);
      }
    });
  }

  @Override
  protected Future<HttpClientConnection> requestConnection2(ContextInternal ctx, long timeout) {
    synchronized (this) {
      if (inflightConnections >= maxPoolSize) {
        Waiter waiter = new Waiter(ctx);
        waiters.add(waiter);
        return waiter.promise.future();
      }
      inflightConnections++;
    }
    return tryConnect(ctx);
  }

  @Override
  public void close() {
    super.close();
    synchronized (this) {
      waiters.forEach(waiter -> {
        waiter.context.runOnContext(v -> {
          waiter.promise.fail("Closed");
        });
      });
      waiters.clear();
    }
  }
}
