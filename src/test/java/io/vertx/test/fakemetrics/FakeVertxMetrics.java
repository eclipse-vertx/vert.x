/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.fakemetrics;

import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.*;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FakeVertxMetrics extends FakeMetricsBase implements VertxMetrics {

  public static AtomicReference<EventBus> eventBus = new AtomicReference<>();

  public FakeVertxMetrics(Vertx vertx) {
    super(vertx);
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

  public EventBusMetrics createMetrics(EventBus eventBus) {
    return new FakeEventBusMetrics(eventBus);
  }

  public HttpServerMetrics<?, ?, ?> createMetrics(HttpServer server, SocketAddress localAddress, HttpServerOptions options) {
    return new FakeHttpServerMetrics(server);
  }

  public HttpClientMetrics<?, ?, ?> createMetrics(HttpClient client, HttpClientOptions options) {
    return new FakeHttpClientMetrics(client);
  }

  public TCPMetrics<?> createMetrics(NetServer server, SocketAddress localAddress, NetServerOptions options) {
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

  public TCPMetrics<?> createMetrics(NetClient client, NetClientOptions options) {
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

  public DatagramSocketMetrics createMetrics(DatagramSocket socket, DatagramSocketOptions options) {
    return new FakeDatagramSocketMetrics(socket);
  }

  @Override
  public <P> PoolMetrics<?> createMetrics(P pool, String poolName, int maxPoolSize) {
    return new FakeThreadPoolMetrics(poolName, maxPoolSize);
  }

  public boolean isEnabled() {
    return true;
  }

  @Override
  public void eventBusInitialized(EventBus bus) {
    this.eventBus.set(bus);
  }
}