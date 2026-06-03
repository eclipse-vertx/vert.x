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
import io.vertx.test.http.HttpTestBase;
import io.vertx.test.tls.Cert;
import org.junit.Test;

import javax.net.ssl.SSLSessionContext;

import static io.vertx.test.core.AssertExpectations.that;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SSLEngineTest extends HttpTestBase {

  private static boolean hasAlpn() {
    return JdkSSLEngineOptions.isAlpnAvailable();
  }

  private static final boolean OPEN_SSL = Boolean.getBoolean("vertx-test-alpn-openssl");

  public SSLEngineTest() {
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
                      boolean useAlpn, HttpVersion version, String error, String expectedEngine, boolean expectCause) {
    doSslEngineTest(
      sslEngineOptions,
      error,
      expectedEngine,
      expectCause,
      opts -> opts.setUseAlpn(useAlpn),
      srv -> srv.requestHandler(req -> {
        assertEquals(req.version(), version);
        assertTrue(req.isSSL());
        req.response().end();
      }),
      (engineOptions, expectedEng) -> {
        client = vertx.createHttpClient(new HttpClientOptions()
          .setSslEngineOptions(engineOptions)
          .setSsl(true)
          .setUseAlpn(useAlpn)
          .setTrustAll(true)
          .setProtocolVersion(version));
        client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
          .compose(req -> req
            .send()
            .expecting(that(resp -> assertEquals(200, resp.statusCode())))
            .compose(HttpClientResponse::end))
          .onComplete(onSuccess(v -> testComplete()));
        await();
      }
    );
  }

  @Test
  public void testWebSocketDefaultEngine() {
    doWebSocketTest(null, null, "jdk");
  }

  @Test
  public void testWebSocketJdkEngine() {
    doWebSocketTest(new JdkSSLEngineOptions(), null, "jdk");
  }

  @Test
  public void testWebSocketOpenSSLEngine() {
    doWebSocketTest(new OpenSSLEngineOptions(), OPEN_SSL ? null : "OpenSSL is not available", "openssl");
  }

  private void doWebSocketTest(SSLEngineOptions sslEngineOptions, String error, String expectedEngine) {
    doSslEngineTest(
      sslEngineOptions,
      error,
      expectedEngine,
      false,
      opts -> {
      },
      srv -> srv.webSocketHandler(ws -> {
        assertTrue(ws.isSsl());
        ws.textMessageHandler(msg -> ws.writeTextMessage(msg));
      }),
      (engineOptions, expectedEng) -> {
        WebSocketClient wsClient = vertx.createWebSocketClient(new WebSocketClientOptions()
          .setSslEngineOptions(engineOptions)
          .setSsl(true)
          .setTrustAll(true));
        wsClient.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
          .onComplete(onSuccess(ws -> {
            assertSslSessionContextType(expectedEng, ws.sslSession().getSessionContext());
            ws.writeTextMessage("test");
            ws.textMessageHandler(msg -> {
              assertEquals("test", msg);
              testComplete();
            });
          }));
        await();
      }
    );
  }

  private void doSslEngineTest(
    SSLEngineOptions sslEngineOptions,
    String error,
    String expectedEngine,
    boolean expectCause,
    java.util.function.Consumer<HttpServerOptions> serverOptionsConfigurator,
    java.util.function.Consumer<HttpServer> serverHandlerSetup,
    java.util.function.BiConsumer<SSLEngineOptions, String> clientTestLogic
  ) {
    server.close();
    HttpServerOptions options = new HttpServerOptions()
      .setSslEngineOptions(sslEngineOptions)
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setKeyCertOptions(Cert.SERVER_PEM.get())
      .setSsl(true);

    serverOptionsConfigurator.accept(options);

    server = vertx.createHttpServer(options);

    serverHandlerSetup.accept(server);

    try {
      startServer();
      if (error != null) {
        fail("Was expecting failure: " + error);
      }
    } catch (Exception e) {
      if (error == null) {
        fail(e);
      } else {
        assertEquals(error, e.getMessage());
        if (expectCause) {
          assertNotSame(e, e.getCause());
        }
        return;
      }
    }
    NetServerInternal tcpServer = ((VertxInternal) vertx).sharedTcpServers().values().iterator().next();
    assertEquals(tcpServer.actualPort(), server.actualPort());
    ServerSslContextProvider provider = tcpServer.sslContextProvider();
    SslContext ctx = provider.createServerContext(null);

    assertSslSessionContextType(expectedEngine != null ? expectedEngine : "jdk", ctx.sessionContext());

    clientTestLogic.accept(sslEngineOptions, expectedEngine != null ? expectedEngine : "jdk");
  }

  private void assertSslSessionContextType(String engine, SSLSessionContext sessionContext) {
    String className = sessionContext.getClass().getName();
    switch (engine) {
      case "jdk":
        assertTrue(
          "Expected JDK SSL session context but got: " + className,
          className.equals("sun.security.ssl.SSLSessionContextImpl")
        );
        break;
      case "openssl":
        assertTrue(
          "Expected OpenSSL session context but got: " + className,
          sessionContext instanceof OpenSslSessionContext
        );
        break;
      default:
        fail("Unknown SSL context type: " + engine);
    }
  }
}
