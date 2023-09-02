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
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.net.impl.pool.ConnectResult;
import io.vertx.core.net.impl.pool.ConnectionPool;
import io.vertx.core.net.impl.pool.PoolConnection;
import io.vertx.core.net.impl.pool.PoolConnector;
import io.vertx.core.net.impl.pool.Lease;
import io.vertx.core.net.impl.pool.PoolWaiter;
import io.vertx.core.spi.metrics.ClientMetrics;

import java.util.List;
import java.util.function.BiFunction;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class SharedClientHttpStreamEndpoint extends ClientHttpEndpointBase<Lease<HttpClientConnection>> implements PoolConnector<HttpClientConnection> {

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

  private final HttpClientImpl client;
  private final HttpChannelConnector connector;
  private final ConnectionPool<HttpClientConnection> pool;

  public SharedClientHttpStreamEndpoint(HttpClientImpl client,
                                        ClientMetrics metrics,
                                        int queueMaxSize,
                                        int http1MaxSize,
                                        int http2MaxSize,
                                        HttpChannelConnector connector,
                                        Runnable dispose) {
    super(metrics, dispose);

    ConnectionPool<HttpClientConnection> pool = ConnectionPool.pool(this, new int[]{http1MaxSize, http2MaxSize}, queueMaxSize)
      .connectionSelector(LIFO_SELECTOR).contextProvider(client.contextProvider());

    this.client = client;
    this.connector = connector;
    this.pool = pool;
  }

  @Override
  public Future<ConnectResult<HttpClientConnection>> connect(ContextInternal context, Listener listener) {
    return connector
      .httpConnect(context)
      .map(connection -> {
        incRefCount();
        connection.evictionHandler(v -> {
          decRefCount();
          listener.onRemove();
        });
        connection.concurrencyChangeHandler(listener::onConcurrencyChange);
        long capacity = connection.concurrency();
        Handler<HttpConnection> connectionHandler = client.connectionHandler();
        if (connectionHandler != null) {
          context.emit(connection, connectionHandler);
        }
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
    return connection.isValid();
  }

  protected void checkExpired() {
    pool.evict(conn -> !conn.isValid(), ar -> {
      if (ar.succeeded()) {
        List<HttpClientConnection> lst = ar.result();
        lst.forEach(HttpConnection::close);
      }
    });
  }

  private class Request implements PoolWaiter.Listener<HttpClientConnection>, Handler<AsyncResult<Lease<HttpClientConnection>>> {

    private final ContextInternal context;
    private final HttpVersion protocol;
    private final long timeout;
    private final Promise<Lease<HttpClientConnection>> promise;
    private long timerID;

    Request(ContextInternal context, HttpVersion protocol, long timeout, Promise<Lease<HttpClientConnection>> promise) {
      this.context = context;
      this.protocol = protocol;
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
          pool.cancel(waiter, ar -> {
            if (ar.succeeded() && ar.result()) {
              promise.fail(new NoStackTraceTimeoutException("The timeout of " + timeout + " ms has been exceeded when getting a connection to " + connector.server()));
            }
          });
        });
      }
    }

    @Override
    public void handle(AsyncResult<Lease<HttpClientConnection>> ar) {
      if (timerID >= 0) {
        context.owner().cancelTimer(timerID);
      }
      promise.handle(ar);
    }

    void acquire() {
      pool.acquire(context, this, protocol == HttpVersion.HTTP_2 ? 1 : 0, this);
    }
  }

  @Override
  protected Future<Lease<HttpClientConnection>> requestConnection2(ContextInternal ctx, long timeout) {
    PromiseInternal<Lease<HttpClientConnection>> promise = ctx.promise();
    Request request = new Request(ctx, client.options().getProtocolVersion(), timeout, promise);
    request.acquire();
    return promise.future();
  }

  @Override
  protected void close() {
    super.close();
    pool.close();
  }
}
