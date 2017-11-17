/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.channel.Channel;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.impl.pool.Pool;
import io.vertx.core.http.impl.pool.Waiter;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * The connection manager associates remote hosts with pools, it also tracks all connections so they can be closed
 * when the manager is closed.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ConnectionManager {

  private final int maxWaitQueueSize;
  private final HttpClientMetrics metrics; // Shall be removed later combining the PoolMetrics with HttpClientMetrics
  private final HttpClientImpl client;
  private final Map<Channel, HttpClientConnection> connectionMap = new ConcurrentHashMap<>();
  private final Map<EndpointKey, Endpoint> endpointMap = new ConcurrentHashMap<>();
  private final long maxSize;

  ConnectionManager(HttpClientImpl client,
                    HttpClientMetrics metrics,
                    long maxSize,
                    int maxWaitQueueSize) {
    this.client = client;
    this.maxWaitQueueSize = maxWaitQueueSize;
    this.metrics = metrics;
    this.maxSize = maxSize;

  }

  private static final class EndpointKey {

    private final boolean ssl;
    private final int port;
    private final String host;

    EndpointKey(boolean ssl, int port, String host) {
      this.ssl = ssl;
      this.host = host;
      this.port = port;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      EndpointKey that = (EndpointKey) o;

      if (ssl != that.ssl) return false;
      if (port != that.port) return false;
      if (!Objects.equals(host, that.host)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = ssl ? 1 : 0;
      result = 31 * result + (host != null ? host.hashCode() : 0);
      result = 31 * result + port;
      return result;
    }
  }

  class Endpoint {

    private final Pool<HttpClientConnection> pool;
    private final Object metric;

    public Endpoint(Pool<HttpClientConnection> pool, Object metric) {
      this.pool = pool;
      this.metric = metric;
    }
  }

  void getConnection(String peerHost, boolean ssl, int port, String host,
                     Handler<HttpConnection> connectionHandler,
                     BiFunction<ContextInternal, HttpClientConnection, Boolean> onSuccess,
                     BiConsumer<ContextInternal, Throwable> onFailure) {
    EndpointKey key = new EndpointKey(ssl, port, peerHost);
    while (true) {
      Endpoint endpoint = endpointMap.computeIfAbsent(key, targetAddress -> {
        int maxPoolSize = Math.max(client.getOptions().getMaxPoolSize(), client.getOptions().getHttp2MaxPoolSize());
        Object metric = metrics != null ? metrics.createEndpoint(host, port, maxPoolSize) : null;
        HttpChannelConnector connector = new HttpChannelConnector(client, metric, ssl, peerHost, host, port);
        Pool<HttpClientConnection> pool = new Pool<>(connector, maxWaitQueueSize, maxSize,
          v -> {
            if (metrics != null) {
              metrics.closeEndpoint(host, port, metric);
            }
            endpointMap.remove(key);
          },
          connectionMap::put,
          connectionMap::remove);
        return new Endpoint(pool, metric);
      });
      Object metric;
      if (metrics != null) {
        metric = metrics.enqueueRequest(endpoint.metric);
      } else {
        metric = null;
      }
      if (endpoint.pool.getConnection(new Waiter<HttpClientConnection>(client.getVertx().getOrCreateContext()) {
        @Override
        public void initConnection(ContextInternal ctx, HttpClientConnection conn) {
          if (connectionHandler != null) {
            ctx.executeFromIO(() -> {
              connectionHandler.handle(conn);
            });
          }
        }
        @Override
        public void handleFailure(ContextInternal ctx, Throwable failure) {
          if (metrics != null) {
            metrics.dequeueRequest(endpoint.metric, metric);
          }
          onFailure.accept(ctx, failure);
        }
        @Override
        public boolean handleConnection(ContextInternal ctx, HttpClientConnection conn) throws Exception {
          if (metrics != null) {
            metrics.dequeueRequest(endpoint.metric, metric);
          }
          return onSuccess.apply(ctx, conn);
        }
      })) {
        break;
      }
    }
  }

  public void close() {
    endpointMap.clear();
    for (HttpClientConnection conn : connectionMap.values()) {
      conn.close();
    }
  }
}
