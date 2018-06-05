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

package io.vertx.test.fakemetrics;

import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.SingleThreadEventLoop;
import io.vertx.core.Verticle;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.transport.Transport;
import io.vertx.core.spi.metrics.DatagramSocketMetrics;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.PoolMetrics;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FakeVertxMetrics extends FakeMetricsBase implements VertxMetrics {

  public static CopyOnWriteArrayList<FakeEventLoopGroupMetrics> eventLoopGroups = new CopyOnWriteArrayList<>();
  public static CopyOnWriteArrayList<FakeEventLoopMetrics> eventLoops = new CopyOnWriteArrayList<>();

  public static void reset() {
    eventLoopGroups.clear();
    eventLoops.clear();
  }

  @Override
  public boolean isMetricsEnabled() {
    return true;
  }

  public void verticleDeployed(Verticle verticle) {
  }

  public void verticleUndeployed(Verticle verticle) {
  }

  public void timerCreated(long id) {
  }

  public void timerEnded(long id, boolean cancelled) {
  }

  public EventBusMetrics createEventBusMetrics() {
    return new FakeEventBusMetrics();
  }

  @Override
  public void eventLoopCreated(final Class<? extends Transport> transport, final String name, final MultithreadEventLoopGroup eventLoopGroup, final SingleThreadEventLoop eventLoop) {
    eventLoops.add(new FakeEventLoopMetrics(transport, name, eventLoopGroup, eventLoop));
  }

  @Override
  public void eventLoopGroupCreated(final Class<? extends Transport> transport, final String name, final MultithreadEventLoopGroup eventLoopGroup) {
    eventLoopGroups.add(new FakeEventLoopGroupMetrics(transport, name, eventLoopGroup));
  }

  public HttpServerMetrics<?, ?, ?> createHttpServerMetrics(HttpServerOptions options, SocketAddress localAddress) {
    return new FakeHttpServerMetrics();
  }

  public HttpClientMetrics<?, ?, ?, ?, Void> createHttpClientMetrics(HttpClientOptions options) {
    return new FakeHttpClientMetrics(options.getMetricsName());
  }

  public TCPMetrics<?> createNetServerMetrics(NetServerOptions options, SocketAddress localAddress) {
    return new TCPMetrics<Object>() {

      public Object connected(SocketAddress remoteAddress, String remoteName) {
        return null;
      }

      public void disconnected(Object socketMetric, SocketAddress remoteAddress) {
      }

      public void bytesRead(Object socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
      }

      public void bytesWritten(Object socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
      }

      public void exceptionOccurred(Object socketMetric, SocketAddress remoteAddress, Throwable t) {
      }

      public boolean isEnabled() {
        return false;
      }

      public void close() {
      }
    };
  }

  public TCPMetrics<?> createNetClientMetrics(NetClientOptions options) {
    return new TCPMetrics<Object>() {

      public Object connected(SocketAddress remoteAddress, String remoteName) {
        return null;
      }

      public void disconnected(Object socketMetric, SocketAddress remoteAddress) {
      }

      public void bytesRead(Object socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
      }

      public void bytesWritten(Object socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
      }

      public void exceptionOccurred(Object socketMetric, SocketAddress remoteAddress, Throwable t) {
      }

      public boolean isEnabled() {
        return false;
      }

      public void close() {
      }
    };
  }

  public DatagramSocketMetrics createDatagramSocketMetrics(DatagramSocketOptions options) {
    return new FakeDatagramSocketMetrics();
  }

  @Override
  public PoolMetrics<?> createPoolMetrics(String poolType, String poolName, int maxPoolSize) {
    return new FakePoolMetrics(poolName, maxPoolSize);
  }

  public boolean isEnabled() {
    return true;
  }

}
