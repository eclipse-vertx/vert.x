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

package io.vertx.test.core;

import io.vertx.core.VertxOptions;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import org.junit.Test;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DummyMetricsTest extends VertxTestBase {

  @Override
  protected VertxOptions getOptions() {
    return new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(false)); // Just to be explicit
  }

  @Test
  public void testDummyVertxMetrics() {
    assertFalse(vertx.isMetricsEnabled());
  }

  @Test
  public void testDummyNetServerMetrics() {
    NetServer server = vertx.createNetServer(new NetServerOptions());
    assertFalse(server.isMetricsEnabled());
  }

  @Test
  public void testDummyNetClientMetrics() {
    NetClient client = vertx.createNetClient(new NetClientOptions());
    assertFalse(client.isMetricsEnabled());
  }

  @Test
  public void testDummyHttpServerMetrics() {
    HttpServer server = vertx.createHttpServer(new HttpServerOptions());
    assertFalse(server.isMetricsEnabled());
  }

  @Test
  public void testDummyHttpClientMetrics() {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions());
    assertFalse(client.isMetricsEnabled());
  }

  @Test
  public void testDummyEventBusMetrics() {
    assertFalse(vertx.eventBus().isMetricsEnabled());
  }

  @Test
  public void testDummyDatagramSocketMetrics() {
    DatagramSocket sock = vertx.createDatagramSocket(new DatagramSocketOptions());
    assertFalse(sock.isMetricsEnabled());
  }
}
