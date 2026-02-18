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
package io.vertx.tests.http;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.http.HttpServerInternal;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakemetrics.FakeHttpServerMetrics;
import io.vertx.test.tls.Cert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.ServerSocket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(LinuxOrOsx.class)
public class CompositeHttpServerTest extends VertxTestBase {

  @Test
  public void testListen() throws Exception {
    expectSucceed();
  }

  @Test
  public void testFailQuic() throws Exception {
    QuicServer quicServer = vertx.createQuicServer(new QuicServerConfig().setPort(4043), new ServerSSLOptions().setKeyCertOptions(Cert.SNI_PEM.get()));
    quicServer.listen().await();
    expectFail();
    quicServer.close().await();
    expectSucceed();
  }

  @Test
  public void testFailTcp() throws Exception {
    try (ServerSocket serverSocket = new ServerSocket(4043)) {
      expectFail();
    }
    expectSucceed();
  }

  public void expectFail() {
    HttpServerConfig config = new HttpServerConfig()
      .setSsl(true)
      .setPort(4043)
      .setVersions(Set.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_3));
    HttpServer server = vertx.createHttpServer(config, new ServerSSLOptions().setKeyCertOptions(Cert.SERVER_JKS.get()));
    Future<HttpServer> res = server.requestHandler(request -> request
        .response()
        .end(request.version().name()))
      .listen();
    assertWaitUntil(res::failed);
  }

  public void expectSucceed() throws Exception {
    expectSucceed(vertx);
  }

  public void expectSucceed(Vertx vertx) throws Exception {
    HttpServerConfig config = new HttpServerConfig()
      .setSsl(true)
      .setPort(4043)
      .setVersions(Set.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_3));

    HttpServer server = vertx.createHttpServer(config, new ServerSSLOptions().setKeyCertOptions(Cert.SERVER_JKS.get()));

    server.requestHandler(request -> request
        .response()
        .end(request.version().name()))
      .listen().await();

    HttpClientConfig clientConfig = new HttpClientConfig()
      .setVersions(List.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_3))
      .setSsl(true);

    HttpClient client = vertx.createHttpClient(clientConfig, new ClientSSLOptions().setTrustAll(true));

    for (HttpVersion version : List.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_3)) {
      RequestOptions o = new RequestOptions().setHost("localhost").setPort(4043).setProtocolVersion(version);
      Buffer resp = client.request(o)
        .compose(request -> request
          .send()
          .compose(HttpClientResponse::body))
        .await();
      assertEquals(version.name(), resp.toString());
    }
  }

  @Test
  public void testAutomaticCleanup() {

    HttpServerConfig config = new HttpServerConfig()
      .setSsl(true)
      .setPort(4043)
      .setVersions(Set.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_3));

    HttpServerInternal server = (HttpServerInternal)vertx.createHttpServer(config, new ServerSSLOptions().setKeyCertOptions(Cert.SERVER_JKS.get()))
      .requestHandler(request -> request
        .response()
        .end(request.version().name()));

    String id = vertx.deployVerticle(context -> server.listen()).await();
    vertx.undeploy(id).await();
    assertTrue(server.isClosed());
  }

  @Test
  public void testMetrics() throws Exception {
    AtomicReference<FakeHttpServerMetrics> metricsRef = new AtomicReference<>();
    Vertx vertx = Vertx.builder()
      .withMetrics(options -> new VertxMetrics() {
        @Override
        public HttpServerMetrics<?, ?> createHttpServerMetrics(HttpServerConfig config, SocketAddress tcpLocalAddress, SocketAddress udpLocalAddress) {
          FakeHttpServerMetrics metrics = new FakeHttpServerMetrics(tcpLocalAddress, udpLocalAddress);
          metricsRef.set(metrics);
          return metrics;
        }
      })
      .build();
    expectSucceed(vertx);
    FakeHttpServerMetrics metrics = metricsRef.get();
    assertNotNull(metricsRef.get());
    assertNotNull(metricsRef.get().tcpLocalAddress());
    assertNotNull(metricsRef.get().udpLocalAddress());
    assertFalse(metrics.isClosed());
    vertx.close().await();
    assertTrue(metrics.isClosed());
  }
}
