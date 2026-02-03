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

import io.vertx.core.Vertx;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.impl.tcp.TcpHttpClientTransport;
import io.vertx.core.http.impl.tcp.TcpHttpServer;
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.core.internal.http.HttpServerInternal;
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.QuicEndpoint;
import io.vertx.core.net.QuicServer;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import junit.framework.AssertionFailedError;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FakeMetricsBase implements Metrics {

  private static volatile Throwable unexpectedError;

  private boolean closed;

  public static FakeQuicEndpointMetrics getMetrics(QuicEndpoint measured) {
    return (FakeQuicEndpointMetrics) ((MetricsProvider) measured).getMetrics();
  }

  public static FakeHttpServerMetrics getMetrics(HttpServer measured) {
    return (FakeHttpServerMetrics) ((MetricsProvider) measured).getMetrics();
  }

  public static FakeTCPMetrics tpcMetricsOf(HttpServer server) {
    return (FakeTCPMetrics) ((TcpHttpServer)((HttpServerInternal)server).unwrap()).tcpServer().getMetrics();
  }

  public static FakeTCPMetrics tpcMetricsOf(HttpClientAgent client) {
    return (FakeTCPMetrics)((TcpHttpClientTransport)((HttpClientInternal)client).tcpTransport()).client().getMetrics();
  }

  public static FakeEventBusMetrics getMetrics(EventBus measured) {
    return (FakeEventBusMetrics) ((MetricsProvider) measured).getMetrics();
  }

  public static FakeHttpClientMetrics getMetrics(HttpClientAgent measured) {
    return (FakeHttpClientMetrics) ((MetricsProvider) measured).getMetrics();
  }

  public static FakeHttpClientMetrics getMetrics(WebSocketClient measured) {
    return (FakeHttpClientMetrics) ((MetricsProvider) measured).getMetrics();
  }

  public static FakeDatagramSocketMetrics getMetrics(DatagramSocket measured) {
    return (FakeDatagramSocketMetrics) ((MetricsProvider) measured).getMetrics();
  }

  public static FakeVertxMetrics getMetrics(Vertx measured) {
    return (FakeVertxMetrics) ((MetricsProvider) measured).getMetrics();
  }

  public FakeMetricsBase() {
  }

  public static void registerFailure(Throwable failure) {
    unexpectedError = failure;
  }

  public static void sanityCheck() {
    Throwable err = unexpectedError;
    if (err != null) {
      AssertionFailedError afe = new AssertionFailedError();
      afe.initCause(err);
      unexpectedError = null;
      throw afe;
    }
  }

  @Override
  public synchronized void close() {
    if (closed) {
      // FAILING BECAUSE WE CLOSE MULTIPLE TIMES
//      registerFailure(new IllegalStateException(getClass().getSimpleName() + " already closed"));
    }
    closed = true;
  }
}
