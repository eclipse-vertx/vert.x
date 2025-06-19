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
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.resource.ManagedResource;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Set of connections (not pooled) to a host used for WebSocket.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class WebSocketGroup extends ManagedResource {

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
  private final ClientMetrics clientMetrics;
  private final PoolMetrics poolMetrics;

  WebSocketGroup(ClientMetrics clientMetrics, PoolMetrics poolMetrics, WebSocketClientOptions options, int maxPoolSize, HttpChannelConnector connector) {
    super();
    this.options = options;
    this.maxPoolSize = maxPoolSize;
    this.connector = connector;
    this.waiters = new ArrayDeque<>();
    this.clientMetrics = clientMetrics;
    this.poolMetrics = poolMetrics;
  }

  public Future<WebSocket> requestConnection(ContextInternal ctx, WebSocketConnectOptions connectOptions, long timeout) {
    Future<WebSocket> fut = requestConnection2(ctx, connectOptions, timeout);
    if (poolMetrics != null) {
      Object metric = poolMetrics.enqueue();
      fut = fut.andThen(ar -> {
        poolMetrics.dequeue(metric);
      });
    }
    return fut;
  }

  private void connect(ContextInternal ctx, WebSocketConnectOptions connectOptions, Promise<WebSocket> promise) {
    ContextInternal eventLoopContext;
    if (ctx.isEventLoopContext()) {
      eventLoopContext = ctx;
    } else {
      eventLoopContext = ctx.toBuilder().withThreadingModel(ThreadingModel.EVENT_LOOP).build();
    }
    Future<HttpClientConnection> fut = connector.httpConnect(eventLoopContext);
    fut.onComplete(ar -> {
      if (ar.succeeded()) {
        HttpClientConnection c = ar.result();
        if (!incRefCount()) {
          c.close();
          promise.fail(new VertxException("Connection closed", true));
          return;
        }
        long timeout = Math.max(connectOptions.getTimeout(), 0L);
        if (connectOptions.getIdleTimeout() >= 0L) {
          timeout = connectOptions.getIdleTimeout();
        }
        Http1xClientConnection ci = (Http1xClientConnection) c;
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
      } else {
        promise.fail(ar.cause());
      }
    });
  }

  private void release() {
    Waiter waiter;
    synchronized (WebSocketGroup.this) {
      if (--inflightConnections > maxPoolSize || waiters.isEmpty()) {
        return;
      }
      waiter = waiters.poll();
    }
    connect(waiter.context, waiter.connectOptions, waiter.promise);
  }

  private Future<WebSocket> tryAcquire(ContextInternal ctx, WebSocketConnectOptions options) {
    synchronized (this) {
      if (inflightConnections >= maxPoolSize) {
        Waiter waiter = new Waiter(ctx, options);
        waiters.add(waiter);
        return waiter.promise.future();
      }
      inflightConnections++;
    }
    return null;
  }

  protected Future<WebSocket> requestConnection2(ContextInternal ctx, WebSocketConnectOptions connectOptions, long timeout) {
    Future<WebSocket> res = tryAcquire(ctx, connectOptions);
    if (res == null) {
      PromiseInternal<WebSocket> promise = ctx.promise();
      connect(ctx, connectOptions, promise);
      res = promise.future();
    }
    res.andThen(ar -> {
      if (ar.succeeded()) {
        WebSocketImpl wsi = (WebSocketImpl) ar.result();
        wsi.evictionHandler(v -> {
          decRefCount();
          release();
        });
      } else {
        decRefCount();
        release();
      }
    });
    return res;
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
  protected void cleanup() {
    if (clientMetrics != null) {
      clientMetrics.close();
    }
    if (poolMetrics != null) {
      poolMetrics.close();
    }
  }
}
