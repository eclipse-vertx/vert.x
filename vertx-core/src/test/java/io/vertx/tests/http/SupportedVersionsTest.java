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

import io.vertx.core.VertxException;
import io.vertx.core.http.*;
import io.vertx.core.http.Http2ClientConfig;
import io.vertx.core.http.HttpClientConfig;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static io.vertx.test.http.AbstractHttpTest.DEFAULT_HTTPS_PORT;
import static io.vertx.test.http.AbstractHttpTest.DEFAULT_HTTP_PORT;

@RunWith(LinuxOrOsx.class)
public class SupportedVersionsTest extends VertxTestBase {

  private static final HttpServerOptions TCP_SERVER_DEFAULT = new HttpServerOptions()
    .setPort(DEFAULT_HTTP_PORT);
  private static final HttpServerOptions TCP_SERVER_DEFAULT_TLS = new HttpServerOptions()
    .setPort(DEFAULT_HTTPS_PORT)
    .setSsl(true)
    .setKeyCertOptions(Cert.SNI_JKS.get());
  private static final HttpServerOptions TCP_SERVER_DEFAULT_TLS_WITH_ALPN = new HttpServerOptions(TCP_SERVER_DEFAULT_TLS)
    .setUseAlpn(true);
  private static final HttpServerConfig QUIC_SERVER_DEFAULT_TLS = new HttpServerConfig()
    .addSupportedVersion(HttpVersion.HTTP_3)
    .setQuicPort(DEFAULT_HTTPS_PORT);

  static {
    // Todo : improve this usability
    QUIC_SERVER_DEFAULT_TLS.getSslOptions().setKeyCertOptions(Cert.SNI_JKS.get());
  }

  private static final HttpClientOptions LEGACY_CLIENT_DEFAULT_TLS = new HttpClientOptions().setSsl(true).setTrustAll(true);
  private static final HttpClientOptions LEGACY_CLIENT_DEFAULT_TLS_WITH_ALPN = new HttpClientOptions(LEGACY_CLIENT_DEFAULT_TLS).setUseAlpn(true);
  private static final HttpClientConfig CLIENT_DEFAULT = new HttpClientConfig();
  private static final HttpClientConfig CLIENT_DEFAULT_TLS = new HttpClientConfig().setSsl(true);

  static {
    CLIENT_DEFAULT.getSslOptions().setTrustAll(true);
    CLIENT_DEFAULT_TLS.getSslOptions().setTrustAll(true);
  }

  private HttpServer tcpServer;
  private HttpServer quicServer;
  private HttpClient client;

  @Override
  protected void tearDown() throws Exception {
    HttpClient c = client;
    client = null;
    if (c != null) {
      c.close().await();
    }
    HttpServer s = tcpServer;
    tcpServer = null;
    if (s != null) {
      s.close().await();
    }
    s = quicServer;
    quicServer = null;
    if (s != null) {
      s.close().await();
    }
    super.tearDown();
  }

  @Test
  public void testLegacyDefaultTest() {
    HttpVersion version = legacyTest(new HttpServerOptions(TCP_SERVER_DEFAULT), new HttpClientOptions());
    assertEquals(HttpVersion.HTTP_1_1, version);
  }

  @Test
  public void testLegacyHttp11Test() {
    HttpVersion version = legacyTest(new HttpServerOptions(TCP_SERVER_DEFAULT), new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1));
    assertEquals(HttpVersion.HTTP_1_1, version);
  }

  @Test
  public void testLegacyTlsTest() {
    HttpVersion version = legacyTest(TCP_SERVER_DEFAULT_TLS, LEGACY_CLIENT_DEFAULT_TLS);
    assertEquals(HttpVersion.HTTP_1_1, version);
  }

  @Test
  public void testLegacyHttp11TlsTest() {
    HttpVersion version = legacyTest(TCP_SERVER_DEFAULT_TLS, new HttpClientOptions(LEGACY_CLIENT_DEFAULT_TLS).setProtocolVersion(HttpVersion.HTTP_1_1));
    assertEquals(HttpVersion.HTTP_1_1, version);
  }

  @Test
  public void testLegacyH2cWithUpgradeTest() {
    HttpVersion version = legacyTest(new HttpServerOptions(TCP_SERVER_DEFAULT), new HttpClientOptions().setHttp2ClearTextUpgrade(false).setProtocolVersion(HttpVersion.HTTP_2));
    assertEquals(HttpVersion.HTTP_2, version);
  }

  @Test
  public void testLegacyH2cPriorKnowledgeTest() {
    HttpVersion version = legacyTest(new HttpServerOptions(TCP_SERVER_DEFAULT), new HttpClientOptions().setHttp2ClearTextUpgrade(true).setProtocolVersion(HttpVersion.HTTP_2));
    assertEquals(HttpVersion.HTTP_2, version);
  }

  @Test
  public void testLegacyH2Test() {
    HttpVersion version = legacyTest(TCP_SERVER_DEFAULT_TLS_WITH_ALPN, new HttpClientOptions(LEGACY_CLIENT_DEFAULT_TLS_WITH_ALPN).setProtocolVersion(HttpVersion.HTTP_2));
    assertEquals(HttpVersion.HTTP_2, version);
  }

  private HttpVersion legacyTest(HttpServerOptions serverOptions, HttpClientOptions clientOptions) {
    tcpServer = vertx
      .createHttpServer(serverOptions)
      .requestHandler(request -> {
        request.response().end();
      });
    int port = tcpServer
      .listen()
      .await().actualPort();
    client = vertx.createHttpClient(clientOptions);
    return client.request(new RequestOptions().setPort(port).setHost("localhost"))
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end)
        .map(v -> request.version()))
      .await();
  }

  @Test
  public void testConfigDefaultTest() {
    HttpVersion version = configTest(new HttpServerOptions(TCP_SERVER_DEFAULT), new HttpClientConfig());
    assertEquals(HttpVersion.HTTP_1_1, version);
  }

  @Test
  public void testConfigHttp11Test() {
    HttpVersion version = configTest(new HttpServerOptions(TCP_SERVER_DEFAULT), new HttpClientConfig().setSupportedVersions(List.of(HttpVersion.HTTP_1_1)));
    assertEquals(HttpVersion.HTTP_1_1, version);
  }

  @Test
  public void testConfigDefaultTlsTest() {
    HttpVersion version = configTest(new HttpServerOptions(TCP_SERVER_DEFAULT_TLS), new HttpClientConfig(LEGACY_CLIENT_DEFAULT_TLS).setSupportedVersions(List.of(HttpVersion.HTTP_1_1)));
    assertEquals(HttpVersion.HTTP_1_1, version);
  }

  @Test
  public void testConfigH2cWithUpgradeTest() {
    try {
      configTest(new HttpServerOptions(TCP_SERVER_DEFAULT), new HttpClientConfig().setSupportedVersions(List.of(HttpVersion.HTTP_2)).setHttp2Config(new Http2ClientConfig().setClearTextUpgrade(true)));
      fail();
    } catch (Exception ignore) {
      // HTTP/1.1 is required
    }
    HttpVersion version = configTest(new HttpServerOptions(TCP_SERVER_DEFAULT), new HttpClientConfig().setSupportedVersions(List.of(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1)).setHttp2Config(new Http2ClientConfig().setClearTextUpgrade(true)));
    assertEquals(HttpVersion.HTTP_2, version);
  }

  @Test
  public void testConfigH2cWithPriorKnowledgeTest() {
    HttpVersion version = configTest(new HttpServerOptions(TCP_SERVER_DEFAULT), new HttpClientConfig().setSupportedVersions(List.of(HttpVersion.HTTP_2)).setHttp2Config(new Http2ClientConfig().setClearTextUpgrade(false)));
    assertEquals(HttpVersion.HTTP_2, version);
  }

  @Test
  public void testConfigH2() {
    HttpVersion version = configTest(new HttpServerOptions(TCP_SERVER_DEFAULT_TLS_WITH_ALPN), new HttpClientConfig(LEGACY_CLIENT_DEFAULT_TLS_WITH_ALPN).setSupportedVersions(List.of(HttpVersion.HTTP_2)));
    assertEquals(HttpVersion.HTTP_2, version);
  }

  @Test
  public void testConfigHttp1H2() {
    HttpVersion version = configTest(new HttpServerOptions(TCP_SERVER_DEFAULT_TLS_WITH_ALPN).setPort(4043), new HttpClientConfig(LEGACY_CLIENT_DEFAULT_TLS_WITH_ALPN).setSupportedVersions(List.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2)));
    assertEquals(HttpVersion.HTTP_1_1, version);
  }

  @Test
  public void testConfigH2Http1() {
    HttpVersion version = configTest(new HttpServerOptions(TCP_SERVER_DEFAULT_TLS_WITH_ALPN).setPort(4043), new HttpClientConfig(LEGACY_CLIENT_DEFAULT_TLS_WITH_ALPN).setSupportedVersions(List.of(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1)));
    assertEquals(HttpVersion.HTTP_2, version);
  }

  @Test
  public void testConfigH3() {
    HttpVersion version = configTest(new HttpServerConfig(QUIC_SERVER_DEFAULT_TLS).setTcpPort(4043), new HttpClientConfig(CLIENT_DEFAULT).setSupportedVersions(List.of(HttpVersion.HTTP_3)));
    assertEquals(HttpVersion.HTTP_3, version);
  }

  @Test
  public void testConfigHttp1H3() {
    HttpVersion version = configTest(new HttpServerOptions(TCP_SERVER_DEFAULT), new HttpServerConfig(QUIC_SERVER_DEFAULT_TLS).setTcpPort(4043), new HttpClientConfig(CLIENT_DEFAULT).setSupportedVersions(List.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_3)));
    assertEquals(HttpVersion.HTTP_1_1, version);
  }

  @Test
  public void testConfigH2H3() {
    HttpVersion version = configTest(new HttpServerOptions(TCP_SERVER_DEFAULT_TLS_WITH_ALPN), new HttpServerConfig(QUIC_SERVER_DEFAULT_TLS).setTcpPort(4043), new HttpClientConfig(CLIENT_DEFAULT).setSsl(true).setSupportedVersions(List.of(HttpVersion.HTTP_2, HttpVersion.HTTP_3)));
    assertEquals(HttpVersion.HTTP_2, version);
  }

  @Test
  public void testConfigH2CH3() {
    HttpVersion version = configTest(new HttpServerOptions(TCP_SERVER_DEFAULT), new HttpServerConfig(QUIC_SERVER_DEFAULT_TLS).setTcpPort(4043), new HttpClientConfig(CLIENT_DEFAULT).setSupportedVersions(List.of(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1, HttpVersion.HTTP_3)));
    assertEquals(HttpVersion.HTTP_2, version);
  }

  @Test
  public void testConfigH2H3NoAlpn() {
    HttpClientConfig config = new HttpClientConfig(CLIENT_DEFAULT_TLS).setSupportedVersions(List.of(HttpVersion.HTTP_2, HttpVersion.HTTP_3));
    config.getSslOptions().setUseAlpn(false);
    try {
      configTest(new HttpServerOptions(TCP_SERVER_DEFAULT_TLS_WITH_ALPN), new HttpServerConfig(QUIC_SERVER_DEFAULT_TLS).setTcpPort(4043), config);
      fail();
    } catch (VertxException e) {
      // Must enable ALPN
    }
  }

  private HttpVersion configTest(HttpServerOptions serverOptions, HttpClientConfig clientConfig) {
    return configTest(serverOptions, null, clientConfig);
  }

  private HttpVersion configTest(HttpServerConfig serverOptions, HttpClientConfig clientConfig) {
    return configTest(null, serverOptions, clientConfig);
  }

  private HttpVersion configTest(HttpServerOptions tcpServerOptions, HttpServerConfig quicServerOptions, HttpClientConfig clientConfig) {
    if (tcpServerOptions != null) {
      tcpServer = vertx
        .createHttpServer(tcpServerOptions)
        .requestHandler(request -> {
          request.response().end();
        });
      tcpServer
        .listen()
        .await();
    }
    if (quicServerOptions != null) {
      quicServer = vertx
        .createHttpServer(quicServerOptions)
        .requestHandler(request -> {
          request.response().end();
        });
      quicServer
        .listen()
        .await();
    }

    if (clientConfig.getDefaultPort() == 80) {
      if (tcpServer != null) {
        clientConfig.setDefaultPort(tcpServer.actualPort());
      } else if (quicServer != null) {
        clientConfig.setDefaultPort(quicServer.actualPort());
      }
    }
    client = vertx.createHttpClient(clientConfig);
    return client.request(new RequestOptions().setHost("localhost"))
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end)
        .map(v -> request.version()))
      .await();
  }

}
