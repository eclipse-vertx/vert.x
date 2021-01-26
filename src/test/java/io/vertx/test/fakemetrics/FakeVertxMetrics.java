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

package io.vertx.test.fakemetrics;

import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FakeVertxMetrics extends FakeMetricsBase implements VertxMetrics {

  private final MetricsOptions options;
  private volatile Vertx vertx;

  public FakeVertxMetrics(MetricsOptions options) {
    this.options = options;
  }

  public FakeVertxMetrics() {
    this.options = new MetricsOptions();
  }

  public MetricsOptions options() {
    return options;
  }

  public Vertx vertx() {
    return vertx;
  }

  @Override
  public boolean isMetricsEnabled() {
    return true;
  }

  public EventBusMetrics createEventBusMetrics() {
    return new FakeEventBusMetrics();
  }

  public HttpServerMetrics<?, ?, ?> createHttpServerMetrics(HttpServerOptions options, SocketAddress localAddress) {
    return new FakeHttpServerMetrics();
  }

  public HttpClientMetrics<?, ?, ?, Void> createHttpClientMetrics(HttpClientOptions options) {
    return new FakeHttpClientMetrics(options.getMetricsName());
  }

  public TCPMetrics<?> createNetServerMetrics(NetServerOptions options, SocketAddress localAddress) {
    return new TCPMetrics<Object>() {
   };
  }

  public TCPMetrics<?> createNetClientMetrics(NetClientOptions options) {
    return new TCPMetrics<Object>() {
    };
  }

  public DatagramSocketMetrics createDatagramSocketMetrics(DatagramSocketOptions options) {
    return new FakeDatagramSocketMetrics();
  }

  @Override
  public PoolMetrics<?> createPoolMetrics(String poolType, String poolName, int maxPoolSize) {
    return new FakePoolMetrics(poolName, maxPoolSize);
  }

  @Override
  public void vertxCreated(Vertx vertx) {
    this.vertx = vertx;
  }
}
