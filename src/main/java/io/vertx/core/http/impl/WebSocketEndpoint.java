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
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.impl.endpoint.Endpoint;
import io.vertx.core.spi.metrics.ClientMetrics;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Set of connections (not pooled) to a host used for WebSocket.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class WebSocketEndpoint extends Endpoint {

  private static class Waiter {

    final Promise<WebSocket> promise;
    final ContextInternal context;
    final WebSocketConnectOptions connectOptions;

    Waiter(ContextInternal context, WebSocketConnectOptions connectOptions) {
      this.promise = context.promise();
      this.context = context;
      this.connectOptions = connectOptions;
    }
  }

  private final WebSocketClientOptions options;
  private final int maxPoolSize;
  private final HttpChannelConnector connector;
  private final Deque<Waiter> waiters;
  private int inflightConnections;

  private final ClientMetrics metrics; // Shall be removed later combining the PoolMetrics with HttpClientMetrics

  WebSocketEndpoint(ClientMetrics metrics, WebSocketClientOptions options, int maxPoolSize, HttpChannelConnector connector, Runnable dispose) {
    super(dispose);
    this.options = options;
    this.maxPoolSize = maxPoolSize;
    this.connector = connector;
    this.waiters = new ArrayDeque<>();
    this.metrics = metrics;
  }

  public Future<WebSocket> requestConnection(ContextInternal ctx, WebSocketConnectOptions connectOptions, long timeout) {
    Future<WebSocket> fut = requestConnection2(ctx, connectOptions, timeout);
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

  private void onEvict() {
    decRefCount();
    Waiter h;
    synchronized (WebSocketEndpoint.this) {
      if (--inflightConnections > maxPoolSize || waiters.isEmpty()) {
        return;
      }
      h = waiters.poll();
    }
    tryConnect(h.context, h.connectOptions).onComplete(h.promise);
  }

  private Future<WebSocket> tryConnect(ContextInternal ctx, WebSocketConnectOptions connectOptions) {
    ContextInternal eventLoopContext;
    if (ctx.isEventLoopContext()) {
      eventLoopContext = ctx;
    } else {
      eventLoopContext = ctx.owner().createEventLoopContext(ctx.nettyEventLoop(), ctx.workerPool(), ctx.classLoader());
    }
    Future<HttpClientConnectionInternal> fut = connector.httpConnect(eventLoopContext);
    return fut.compose(c -> {
      if (!incRefCount()) {
        c.close();
        return Future.failedFuture(new VertxException("Connection closed", true));
      }
      long timeout = Math.max(connectOptions.getTimeout(), 0L);
      if (connectOptions.getIdleTimeout() >= 0L) {
        timeout = connectOptions.getIdleTimeout();
      }
      Http1xClientConnection ci = (Http1xClientConnection) c;
      Promise<WebSocket> promise = ctx.promise();
      ci.toWebSocket(
        ctx,
        connectOptions.getURI(),
        connectOptions.getHeaders(),
        connectOptions.getAllowOriginHeader(),
        options,
        connectOptions.getVersion(),
        connectOptions.getSubProtocols(),
        timeout,
        connectOptions.isRegisterWriteHandlers(),
        options.getMaxFrameSize(),
        promise);
      return promise.future().andThen(ar -> {
        if (ar.succeeded()) {
          WebSocketImpl wsi = (WebSocketImpl) ar.result();
          wsi.evictionHandler(v -> onEvict());
        } else {
          onEvict();
        }
      });
    });
  }

  protected Future<WebSocket> requestConnection2(ContextInternal ctx, WebSocketConnectOptions connectOptions, long timeout) {
    synchronized (this) {
      if (inflightConnections >= maxPoolSize) {
        Waiter waiter = new Waiter(ctx, connectOptions);
        waiters.add(waiter);
        return waiter.promise.future();
      }
      inflightConnections++;
    }
    return tryConnect(ctx, connectOptions);
  }

  @Override
  public void handleShutdown() {
    synchronized (this) {
      for (Waiter waiter : waiters) {
        waiter.promise.fail("Closed");
      }
      waiters.clear();
    }
  }

  @Override
  protected void dispose() {
    if (metrics != null) {
      metrics.close();
    }
  }
}
