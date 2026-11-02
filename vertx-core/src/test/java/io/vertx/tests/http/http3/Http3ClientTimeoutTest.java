/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http.http3;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.HttpClientBuilder;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.http.HttpConfigurator;
import io.vertx.tests.http.HttpClientTimeoutTest;
import org.junit.Ignore;
import org.junit.Test;

public class Http3ClientTimeoutTest extends HttpClientTimeoutTest {

  private final HttpConfigurator config = Http3Configurator.INSTANCE;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    testAddress = SocketAddress.inetSocketAddress(config.port(), config.host());
    requestOptions = new RequestOptions()
      .setHost(config.host())
      .setPort(config.port())
      .setURI(DEFAULT_TEST_URI);
  }

  @Override
  protected HttpServer createHttpServer() {
    return config.forServer().create(vertx);
  }

  @Override
  protected HttpClientAgent createHttpClient() {
    return config.forClient().create(vertx);
  }

  @Override
  protected HttpClientBuilder httpClientBuilder(Vertx vertx) {
    return config.forClient().builder(vertx);
  }

  @Ignore("Requires a saturated connection pool, needs stream concurrency limit support in the HTTP/3 test configurator")
  @Test
  @Override
  public void testConnectTimeoutDoesFire() throws Exception {
  }

  @Ignore("Requires a saturated connection pool, needs stream concurrency limit support in the HTTP/3 test configurator")
  @Test
  @Override
  public void testConnectTimeoutDoesNotFire() throws Exception {
  }

  @Ignore("Recreates the client from HttpClientOptions which cannot connect to an HTTP/3 server")
  @Test
  @Override
  public void testRequestsTimeoutInQueue(Checkpoint checkpoint) throws Exception {
  }

  @Ignore("Recreates the client from HttpClientOptions which cannot connect to an HTTP/3 server")
  @Test
  @Override
  public void testRequestTimeoutIsNotDelayedAfterResponseIsReceived(Checkpoint checkpoint) throws Exception {
  }

  @Ignore("Request idle timeout does not fire on HTTP/3 streams yet")
  @Test
  @Override
  public void testRequestTimesOutWhenIndicatedPeriodExpiresWithoutAResponseFromRemoteServer(Checkpoint checkpoint) throws Exception {
  }

  @Ignore("Bypasses the HTTP/3 client under test with an HttpClientOptions based client and a NetServer")
  @Test
  @Override
  public void testTimedOutWaiterDoesNotConnect(Checkpoint checkpoint) throws Exception {
  }
}
