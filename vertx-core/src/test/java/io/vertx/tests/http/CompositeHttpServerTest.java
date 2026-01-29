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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.*;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.ServerSocket;
import java.util.List;
import java.util.Set;

@RunWith(LinuxOrOsx.class)
public class CompositeHttpServerTest extends VertxTestBase {

  @Test
  public void testListen() throws Exception {
    expectSucceed();
  }

  @Test
  public void testFailQuic() throws Exception {
    QuicServer quicServer = QuicServer.create(vertx, new QuicServerConfig().setPort(4043), new ServerSSLOptions().setKeyCertOptions(Cert.SNI_PEM.get()));
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

  public void expectFail() throws Exception {
    HttpServerConfig config = new HttpServerConfig()
      .setSsl(true)
      .setPort(4043)
      .setVersions(Set.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_3));
    config.getSslOptions().setKeyCertOptions(Cert.SERVER_JKS.get());
    HttpServer server = vertx.createHttpServer(config);
    Future<HttpServer> res = server.requestHandler(request -> request
        .response()
        .end(request.version().name()))
      .listen();
    assertWaitUntil(res::failed);
  }

  public void expectSucceed() throws Exception {
    HttpServerConfig config = new HttpServerConfig()
      .setSsl(true)
      .setPort(4043)
      .setVersions(Set.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_3));

    config.getSslOptions().setKeyCertOptions(Cert.SERVER_JKS.get());

    HttpServer server = vertx.createHttpServer(config);

    server.requestHandler(request -> request
        .response()
        .end(request.version().name()))
      .listen().await();

    HttpClientConfig clientConfig = new HttpClientConfig()
      .setVersions(List.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_3))
      .setSsl(true);
    clientConfig.getSslOptions().setTrustAll(true);

    HttpClient client = vertx.createHttpClient(clientConfig);

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
}
