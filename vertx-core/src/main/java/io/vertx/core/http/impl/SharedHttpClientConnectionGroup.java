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
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.impl.http1x.Http1xClientConnection;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.impl.NoStackTraceTimeoutException;
import io.vertx.core.internal.http.HttpChannelConnector;
import io.vertx.core.internal.pool.ConnectResult;
import io.vertx.core.internal.pool.ConnectionPool;
import io.vertx.core.internal.pool.PoolConnection;
import io.vertx.core.internal.pool.PoolConnector;
import io.vertx.core.internal.pool.Lease;
import io.vertx.core.internal.pool.PoolWaiter;
import io.vertx.core.internal.resource.ManagedResource;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class SharedHttpClientConnectionGroup extends ManagedResource {

  /**
   * LIFO pool selector.
   */
  private static final BiFunction<PoolWaiter<HttpClientConnection>, List<PoolConnection<HttpClientConnection>>, PoolConnection<HttpClientConnection>> LIFO_SELECTOR = (waiter, connections) -> {
    int size = connections.size();
    PoolConnection<HttpClientConnection> selected = null;
    long last = 0L;
    for (int i = 0; i < size; i++) {
      PoolConnection<HttpClientConnection> pooled = connections.get(i);
      if (pooled.available() > 0) {
        HttpClientConnection conn = pooled.get();
        if (selected == null) {
          selected = pooled;
        } else {
          if (conn.lastResponseReceivedTimestamp() > last) {
            selected = pooled;
          }
        }
      }
    }
    return selected;
  };

  private final PoolMetrics poolMetrics;
  private final ClientMetrics clientMetrics;
  private final Handler<HttpConnection> connectHandler;
  private final Pool pool;
  private final HostAndPort authority;
  private final SocketAddress server;

  public SharedHttpClientConnectionGroup(ClientMetrics clientMetrics,
                                         Handler<HttpConnection> connectHandler,
                                         Function<SharedHttpClientConnectionGroup, Pool> poolProvider,
                                         PoolMetrics poolMetrics,
                                         HostAndPort authority,
                                         SocketAddress server) {
    this.poolMetrics = poolMetrics;
    this.clientMetrics = clientMetrics;
    this.authority = authority;
    this.pool = poolProvider.apply(this);
    this.server = server;
    this.connectHandler = connectHandler;
  }

  protected void checkExpired() {
    pool.checkExpired();
  }

  private class Request implements PoolWaiter.Listener<HttpClientConnection>, Completable<Lease<HttpClientConnection>> {

    private final ContextInternal context;
    private final long timeout;
    private final Promise<Lease<HttpClientConnection>> promise;
    private long timerID;

    Request(ContextInternal context, long timeout, Promise<Lease<HttpClientConnection>> promise) {
      this.context = context;
      this.timeout = timeout;
      this.promise = promise;
      this.timerID = -1L;
    }

    @Override
    public void onEnqueue(PoolWaiter<HttpClientConnection> waiter) {
      onConnect(waiter);
    }

    @Override
    public void onConnect(PoolWaiter<HttpClientConnection> waiter) {
      if (timeout > 0L && timerID == -1L) {
        timerID = context.setTimer(timeout, id -> {
          pool.pool.cancel(waiter, (res, err) -> {
            if (err == null & res)
              promise.fail(new NoStackTraceTimeoutException("The timeout of " + timeout + " ms has been exceeded when getting a connection to " + server));
            });
        });
      }
    }

    @Override
    public void complete(Lease<HttpClientConnection> result, Throwable failure) {
      if (timerID >= 0) {
        context.owner().cancelTimer(timerID);
      }
      promise.complete(result, failure);
    }

    void acquire() {
      pool.acquire(context,this, promise);
    }
  }

  public Future<Lease<HttpClientConnection>> requestConnection(ContextInternal ctx, long timeout) {
    Promise<Lease<HttpClientConnection>> promise = ctx.promise();
    Future<Lease<HttpClientConnection>> fut = promise.future();
    // ctx.workerPool() -> not sure we want that in a pool
    ContextInternal connCtx = ctx.toBuilder().withThreadingModel(ThreadingModel.EVENT_LOOP).build();
    Request request = new Request(connCtx, timeout, promise);
    request.acquire();
    if (poolMetrics != null) {
      Object metric = poolMetrics.enqueue();
      fut = fut.andThen(ar -> {
        poolMetrics.dequeue(metric);
      });
    }
    return fut;
  }

  @Override
  protected void handleClose() {
    pool.close();
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

  private void init(HttpClientConnection connection) {
    incRefCount();
    if (connectHandler != null) {
      ContextInternal context = connection.context();
      context.emit(connection, connectHandler);
    }
  }

  private void dispose(HttpClientConnection connection) {
    decRefCount();
  }

  static class Pool implements PoolConnector<HttpClientConnection> {

    private final SharedHttpClientConnectionGroup owner;
    private final HttpChannelConnector connector;
    private final long maxLifetimeMillis;
    private final HttpConnectParams connectParams;
    private final ConnectionPool<HttpClientConnection> pool;
    private final int poolKind;

    Pool(SharedHttpClientConnectionGroup owner,
                HttpChannelConnector connector,
                int queueMaxSize,
                int http1MaxSize,
                int http2MaxSize,
                long maxLifetimeMillis,
                int initialPoolKind,
                HttpConnectParams connectParams,
                Function<ContextInternal,
                ContextInternal> contextProvider) {
      this.owner = owner;
      this.connector = connector;
      this.maxLifetimeMillis = maxLifetimeMillis;
      this.connectParams = connectParams;
      this.poolKind = initialPoolKind;
      this.pool = ConnectionPool
        .pool(this, new int[]{http1MaxSize, http2MaxSize}, queueMaxSize)
        .connectionSelector(LIFO_SELECTOR)
        .contextProvider(contextProvider);
    }

    @Override
    public Future<ConnectResult<HttpClientConnection>> connect(ContextInternal context, Listener listener) {
      return connector
        .httpConnect(context, owner.server, owner.authority, connectParams, owner.clientMetrics)
        .map(connection -> {
          connection.evictionHandler(v -> {
            owner.dispose(connection);
            listener.onRemove();
          });
          connection.concurrencyChangeHandler(listener::onConcurrencyChange);
          owner.init(connection);
          long capacity = connection.concurrency();
          int idx;
          if (connection instanceof Http1xClientConnection) {
            idx = 0;
          } else {
            idx = 1;
          }
          return new ConnectResult<>(connection, capacity, idx);
        });
    }

    @Override
    public boolean isValid(HttpClientConnection connection) {
      return connection.isValid() && (maxLifetimeMillis == 0L || (System.currentTimeMillis() - connection.creationTimestamp()) <= maxLifetimeMillis);
    }

    void checkExpired() {
      pool
        .evict(c -> !isValid(c), (lst, err) -> {
          if (err == null) {
            lst.forEach(HttpConnection::close);
          }
        });
    }

    void acquire(ContextInternal context, PoolWaiter.Listener<HttpClientConnection> listener, Promise<Lease<HttpClientConnection>> promise) {
      pool.acquire(context, listener, poolKind, promise);
    }

    void close() {
      pool.close((res, err) -> {});
    }
  }
}
