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

package io.vertx.core.http.impl;

import io.netty.channel.Channel;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.pool.Pool;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
  private final HttpVersion version;
  private final long maxSize;
  private long timerID;

  ConnectionManager(HttpClientImpl client,
                    HttpClientMetrics metrics,
                    HttpVersion version,
                    long maxSize,
                    int maxWaitQueueSize) {
    this.client = client;
    this.maxWaitQueueSize = maxWaitQueueSize;
    this.metrics = metrics;
    this.maxSize = maxSize;
    this.version = version;
  }

  synchronized void start() {
    long period = client.getOptions().getPoolCleanerPeriod();
    this.timerID = period > 0 ? client.getVertx().setTimer(period, id -> checkExpired(period)) : -1;
  }

  private synchronized void checkExpired(long period) {
    long timestamp = System.currentTimeMillis();
    endpointMap.values().forEach(e -> e.pool.closeIdle(timestamp));
    timerID = client.getVertx().setTimer(period, id -> checkExpired(period));
  }

  private static final class EndpointKey {

    private final boolean ssl;
    private final int port;
    private final String peerHost;
    private final String host;

    EndpointKey(boolean ssl, int port, String peerHost, String host) {
      if (host == null) {
        throw new NullPointerException("No null host");
      }
      if (peerHost == null) {
        throw new NullPointerException("No null peer host");
      }
      this.ssl = ssl;
      this.peerHost = peerHost;
      this.host = host;
      this.port = port;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      EndpointKey that = (EndpointKey) o;
      return ssl == that.ssl && port == that.port && peerHost.equals(that.peerHost) && host.equals(that.host);
    }

    @Override
    public int hashCode() {
      int result = ssl ? 1 : 0;
      result = 31 * result + peerHost.hashCode();
      result = 31 * result + host.hashCode();
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

  void getConnection(ContextInternal ctx, String peerHost, boolean ssl, int port, String host, Handler<AsyncResult<HttpClientConnection>> handler) {
    EndpointKey key = new EndpointKey(ssl, port, peerHost, host);
    while (true) {
      Endpoint endpoint = endpointMap.computeIfAbsent(key, targetAddress -> {
        int maxPoolSize = Math.max(client.getOptions().getMaxPoolSize(), client.getOptions().getHttp2MaxPoolSize());
        Object metric = metrics != null ? metrics.createEndpoint(host, port, maxPoolSize) : null;
        HttpChannelConnector connector = new HttpChannelConnector(client, metric, version, ssl, peerHost, host, port);
        Pool<HttpClientConnection> pool = new Pool<>(ctx, connector, maxWaitQueueSize, connector.weight(), maxSize,
          v -> {
            if (metrics != null) {
              metrics.closeEndpoint(host, port, metric);
            }
            endpointMap.remove(key);
          },
          conn -> connectionMap.put(conn.channel(), conn),
          conn -> connectionMap.remove(conn.channel(), conn),
          false);
        return new Endpoint(pool, metric);
      });
      Object metric;
      if (metrics != null) {
        metric = metrics.enqueueRequest(endpoint.metric);
      } else {
        metric = null;
      }

      if (endpoint.pool.getConnection(ar -> {
        if (ar.succeeded()) {

          HttpClientConnection conn = ar.result();

          if (metrics != null) {
            metrics.dequeueRequest(endpoint.metric, metric);
          }

          ctx.schedule(Future.succeededFuture(conn), handler);
        } else {
          if (metrics != null) {
            metrics.dequeueRequest(endpoint.metric, metric);
          }
          ctx.schedule(Future.failedFuture(ar.cause()), handler);
        }
      })) {
        break;
      }
    }
  }

  public void close() {
    synchronized (this) {
      if (timerID >= 0) {
        client.getVertx().cancelTimer(timerID);
        timerID = -1;
      }
    }
    endpointMap.clear();
    for (HttpClientConnection conn : connectionMap.values()) {
      conn.close();
    }
  }
}
