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

package io.vertx.it.tls;

import io.netty.handler.ssl.OpenSslSessionContext;
import io.netty.handler.ssl.SslContext;
import io.vertx.core.http.*;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.net.NetServerInternal;
import io.vertx.core.internal.tls.ServerSslContextProvider;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.http.HttpTestBase2;
import io.vertx.test.tls.Cert;
import org.junit.Test;

import javax.net.ssl.SSLSessionContext;

import static io.vertx.test.core.AssertExpectations.that;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SSLEngineTest extends HttpTestBase2 {

  private static final boolean OPEN_SSL = Boolean.getBoolean("vertx-test-alpn-openssl");

  private static boolean hasAlpn() {
    return JdkSSLEngineOptions.isAlpnAvailable();
  }

  @Test
  public void testDefaultEngineWithAlpn() {
    doTest(null, true, HttpVersion.HTTP_2, hasAlpn() | OPEN_SSL ? null : "ALPN not available for JDK SSL/TLS engine", hasAlpn() ? "jdk" : "openssl", false);
  }

  @Test
  public void testJdkEngineWithAlpn() {
    doTest(new JdkSSLEngineOptions(), true, HttpVersion.HTTP_2, hasAlpn() ? null : "ALPN not available for JDK SSL/TLS engine", "jdk", false);
  }

  @Test
  public void testOpenSSLEngineWithAlpn() {
    doTest(new OpenSSLEngineOptions(), true, HttpVersion.HTTP_2, OPEN_SSL ? null : "OpenSSL is not available", "openssl", true);
  }

  @Test
  public void testDefaultEngine() {
    doTest(null, false, HttpVersion.HTTP_1_1, null, "jdk", false);
  }

  @Test
  public void testJdkEngine() {
    doTest(new JdkSSLEngineOptions(), false, HttpVersion.HTTP_1_1, null, "jdk", false);
  }

  @Test
  public void testOpenSSLEngine() {
    doTest(new OpenSSLEngineOptions(), false, HttpVersion.HTTP_1_1, OPEN_SSL ? null : "OpenSSL is not available", "openssl", true);
  }

  private void doTest(SSLEngineOptions sslEngineOptions,
                      boolean useAlpn,
                      HttpVersion version,
                      String error,
                      String expectedEngine,
                      boolean expectCause) {
    TestServer test = new TestServer(new HttpServerOptions()
      .setSslEngineOptions(sslEngineOptions)
      .setUseAlpn(useAlpn), expectedEngine);
    server.requestHandler(req -> {
      assertEquals(req.version(), version);
      assertTrue(req.isSSL());
      req.response().end();
    });
    try {
      test.setUp();
      assertNull("Was expecting failure: " + error, error);
    } catch (Exception e) {
      assertNotNull(error);
      assertEquals(error, e.getMessage());
      if (expectCause) {
        assertNotSame(e, e.getCause());
      }
      return;
    }
    client = vertx.createHttpClient(new HttpClientOptions()
      .setSslEngineOptions(sslEngineOptions)
      .setSsl(true)
      .setUseAlpn(useAlpn)
      .setTrustAll(true)
      .setProtocolVersion(version));
    client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .compose(req -> req
        .send()
        .expecting(that(resp -> assertEquals(200, resp.statusCode())))
        .compose(HttpClientResponse::end))
      .await();
  }

  @Test
  public void testWebSocketDefaultEngine(Checkpoint checkpoint) {
    doWebSocketTest(checkpoint, null, null, "jdk");
  }

  @Test
  public void testWebSocketJdkEngine(Checkpoint checkpoint) {
    doWebSocketTest(checkpoint, new JdkSSLEngineOptions(), null, "jdk");
  }

  @Test
  public void testWebSocketOpenSSLEngine(Checkpoint checkpoint) {
    doWebSocketTest(checkpoint, new OpenSSLEngineOptions(), OPEN_SSL ? null : "OpenSSL is not available", "openssl");
  }

  private void doWebSocketTest(Checkpoint checkpoint, SSLEngineOptions sslEngineOptions, String error, String expectedEngine) {
    TestServer test = new TestServer(new HttpServerOptions()
      .setSslEngineOptions(sslEngineOptions), expectedEngine);
    server.webSocketHandler(ws -> {
      assertTrue(ws.isSsl());
      ws.textMessageHandler(ws::writeTextMessage);
    });
    try {
      test.setUp();
      assertNull("Was expecting failure: " + error, error);
    } catch (Exception e) {
      assertNotNull(error);
      assertEquals(error, e.getMessage());
      checkpoint.succeed();
      return;
    }
    WebSocketClient wsClient = vertx.createWebSocketClient(new WebSocketClientOptions()
      .setSslEngineOptions(sslEngineOptions)
      .setSsl(true)
      .setTrustAll(true));
    WebSocket ws = wsClient.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/").await();
    test.assertSslSessionContextType(ws.sslSession().getSessionContext());
    ws.writeTextMessage("test");
    ws.textMessageHandler(msg -> {
      assertEquals("test", msg);
      checkpoint.succeed();
    });
  }

  private class TestServer {

    final String expectedEngine;
    String resolvedEngine;

    TestServer(HttpServerOptions options, String expectedEngine) {
      this.expectedEngine = expectedEngine;
      server = vertx.createHttpServer(options
        .setPort(DEFAULT_HTTP_PORT)
        .setHost(DEFAULT_HTTP_HOST)
        .setKeyCertOptions(Cert.SERVER_PEM.get())
        .setSsl(true));
    }

    void setUp() throws Exception {
      startServer();
      resolvedEngine = expectedEngine != null ? expectedEngine : "jdk";
      NetServerInternal tcpServer = ((VertxInternal) vertx).sharedTcpServers().values().iterator().next();
      assertEquals(tcpServer.actualPort(), server.actualPort());
      ServerSslContextProvider provider = tcpServer.sslContextProvider();
      SslContext ctx = provider.createServerContext(null);
      assertSslSessionContextType(ctx.sessionContext());
    }

    void assertSslSessionContextType(SSLSessionContext sessionContext) {
      String className = sessionContext.getClass().getName();
      switch (resolvedEngine) {
        case "jdk":
          assertEquals("Expected JDK SSL session context but got: " + className, "sun.security.ssl.SSLSessionContextImpl", className);
          break;
        case "openssl":
          assertTrue(
            "Expected OpenSSL session context but got: " + className,
            sessionContext instanceof OpenSslSessionContext
          );
          break;
        default:
          fail("Unknown SSL context type: " + resolvedEngine);
      }
    }
  }
}
