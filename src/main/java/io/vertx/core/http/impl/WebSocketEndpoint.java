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
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.metrics.ClientMetrics;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Set of connections (not pooled) to a host used for WebSocket.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class WebSocketEndpoint extends ClientHttpEndpointBase {

  private final int maxPoolSize;
  private final HttpChannelConnector connector;
  private final Deque<Waiter> waiters;
  private int inflightConnections;

  WebSocketEndpoint(ClientMetrics metrics, int port, String host, Object metric, int maxPoolSize, HttpChannelConnector connector, Runnable dispose) {
    super(metrics, port, host, metric, dispose);
    this.maxPoolSize = maxPoolSize;
    this.connector = connector;
    this.waiters = new ArrayDeque<>();
  }

  private static class Waiter {
    final Handler<AsyncResult<HttpClientConnection>> handler;
    final ContextInternal context;
    Waiter(Handler<AsyncResult<HttpClientConnection>> handler, ContextInternal context) {
      this.handler = handler;
      this.context = context;
    }
  }

  private void tryConnect(ContextInternal ctx, Handler<AsyncResult<HttpClientConnection>> handler) {

    class Listener implements Handler<AsyncResult<HttpClientConnection>> {

      private HttpClientConnection conn;

      private void onEvict() {
        connectionRemoved(conn);
        Waiter h;
        synchronized (WebSocketEndpoint.this) {
          if (--inflightConnections > maxPoolSize || waiters.isEmpty()) {
            return;
          }
          h = waiters.poll();
        }
        tryConnect(h.context, h.handler);
      }

      @Override
      public void handle(AsyncResult<HttpClientConnection> ar) {
        if (ar.succeeded()) {
          HttpClientConnection c = ar.result();
          c.lifecycleHandler(recycle -> {
            if (!recycle) {
              onEvict();
            }
          });
          conn = c;
          connectionAdded(c);
          handler.handle(Future.succeededFuture(c));
        } else {
          handler.handle(Future.failedFuture(ar.cause()));
        }
      }
    }
    Listener listener = new Listener();
    Promise<HttpClientConnection> promise = Promise.promise();
    promise.future().onComplete(listener);
    connector.connect((EventLoopContext) ctx, promise);
  }

  @Override
  public void requestConnection2(ContextInternal ctx, Handler<AsyncResult<HttpClientConnection>> handler) {
    synchronized (this) {
      if (inflightConnections >= maxPoolSize) {
        waiters.add(new Waiter(handler, ctx));
        return;
      }
      inflightConnections++;
    }
    tryConnect(ctx, handler);
  }

  @Override
  public void close() {
    super.close();
    synchronized (this) {
      waiters.forEach(waiter -> {
        waiter.context.runOnContext(v -> {
          waiter.handler.handle(Future.failedFuture("Closed"));
        });
      });
      waiters.clear();
    }
  }
}
