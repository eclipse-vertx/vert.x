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

import io.vertx.core.Verticle;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FakeVertxMetrics extends FakeMetricsBase implements VertxMetrics {

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
