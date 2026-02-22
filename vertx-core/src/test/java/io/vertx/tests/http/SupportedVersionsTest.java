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

import io.vertx.core.http.*;
import io.vertx.core.http.HttpClientConfig;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

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
  private static final HttpServerConfig QUIC_SERVER = new HttpServerConfig()
    .setVersions(HttpVersion.HTTP_3)
    .setQuicPort(DEFAULT_HTTPS_PORT);
  private static final ServerSSLOptions DEFAULT_SERVER_TLS = new ServerSSLOptions().setKeyCertOptions(Cert.SNI_JKS.get()).removeEnabledSecureTransportProtocol("TLSv1.3");
  private static final ServerSSLOptions DEFAULT_SERVER_TLS_NO_ALPN = new ServerSSLOptions().setKeyCertOptions(Cert.SNI_JKS.get()).setUseAlpn(false);
  private static final ClientSSLOptions DEFAULT_CLIENT_TLS = new ClientSSLOptions().setTrustAll(true).removeEnabledSecureTransportProtocol("TLSv1.3");

  private static final HttpClientOptions LEGACY_CLIENT_DEFAULT_TLS = new HttpClientOptions().setSsl(true).setTrustAll(true);
  private static final HttpClientOptions LEGACY_CLIENT_DEFAULT_TLS_WITH_ALPN = new HttpClientOptions(LEGACY_CLIENT_DEFAULT_TLS);
  private static final HttpClientConfig CLIENT_DEFAULT = new HttpClientConfig();
  private static final HttpClientConfig CLIENT_DEFAULT_TLS = new HttpClientConfig().setSsl(true);

  @Test
  public void testLegacyDefaultTest() {
    HttpServerOptions server = new HttpServerOptions(TCP_SERVER_DEFAULT);
    HttpClientOptions client = new HttpClientOptions();
    HttpVersion version = testDefaultVersion(server, client);
    assertEquals(HttpVersion.HTTP_1_1, version);
    List<HttpVersion> accepted = testAcceptedVersions(server, client);
    assertEquals(Arrays.asList(HttpVersion.HTTP_1_0, HttpVersion.HTTP_1_1, null), accepted);
  }

  @Test
  public void testLegacyHttp11Test() {
    HttpServerOptions server = new HttpServerOptions(TCP_SERVER_DEFAULT);
    HttpClientOptions client = new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1);
    HttpVersion version = testDefaultVersion(server, client);
    assertEquals(HttpVersion.HTTP_1_1, version);
    List<HttpVersion> accepted = testAcceptedVersions(server, client);
    assertEquals(Arrays.asList(HttpVersion.HTTP_1_0, HttpVersion.HTTP_1_1, null), accepted);
  }

  @Test
  public void testLegacyTlsTest() {
    HttpServerOptions server = TCP_SERVER_DEFAULT_TLS;
    HttpClientOptions client = LEGACY_CLIENT_DEFAULT_TLS;
    HttpVersion version = testDefaultVersion(server, client);
    assertEquals(HttpVersion.HTTP_1_1, version);
    List<HttpVersion> accepted = testAcceptedVersions(server, client);
    assertEquals(Arrays.asList(HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1, null), accepted);
  }

  @Test
  public void testLegacyHttp11TlsTest() {
    HttpServerOptions server = TCP_SERVER_DEFAULT_TLS;
    HttpClientOptions client = new HttpClientOptions(LEGACY_CLIENT_DEFAULT_TLS).setProtocolVersion(HttpVersion.HTTP_1_1);
    HttpVersion version = testDefaultVersion(server, client);
    assertEquals(HttpVersion.HTTP_1_1, version); // Depends on server : check that
    List<HttpVersion> accepted = testAcceptedVersions(server, client);
    assertEquals(Arrays.asList(HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1, null), accepted);
  }

  @Test
  public void testLegacyH2cWithUpgradeTest() {
    HttpServerOptions server = new HttpServerOptions(TCP_SERVER_DEFAULT);
    HttpClientOptions client = new HttpClientOptions().setHttp2ClearTextUpgrade(false).setProtocolVersion(HttpVersion.HTTP_2);
    HttpVersion version = testDefaultVersion(server, client);
    assertEquals(HttpVersion.HTTP_2, version);
    List<HttpVersion> accepted = testAcceptedVersions(server, client);
    assertEquals(List.of(HttpVersion.HTTP_1_0, HttpVersion.HTTP_1_1, HttpVersion.HTTP_2), accepted);
  }

  @Test
  public void testLegacyH2cPriorKnowledgeTest() {
    HttpServerOptions server = new HttpServerOptions(TCP_SERVER_DEFAULT);
    HttpClientOptions client = new HttpClientOptions().setHttp2ClearTextUpgrade(true).setProtocolVersion(HttpVersion.HTTP_2);
    HttpVersion version = testDefaultVersion(server, client);
    assertEquals(HttpVersion.HTTP_2, version);
    List<HttpVersion> accepted = testAcceptedVersions(server, client);
    assertEquals(List.of(HttpVersion.HTTP_1_0, HttpVersion.HTTP_1_1, HttpVersion.HTTP_2), accepted);
  }

  @Test
  public void testLegacyH2Test() {
    HttpServerOptions server = TCP_SERVER_DEFAULT_TLS_WITH_ALPN;
    HttpClientOptions client = new HttpClientOptions(LEGACY_CLIENT_DEFAULT_TLS_WITH_ALPN).setProtocolVersion(HttpVersion.HTTP_2);
    HttpVersion version = testDefaultVersion(server, client);
    assertEquals(HttpVersion.HTTP_2, version);
    List<HttpVersion> accepted = testAcceptedVersions(server, client);
    assertEquals(Arrays.asList(HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1, HttpVersion.HTTP_2), accepted);
  }

  private HttpVersion testDefaultVersion(HttpServerOptions serverOptions, HttpClientOptions clientOptions) {
    return testDefaultVersion(() -> vertx.createHttpServer(serverOptions), () -> vertx.createHttpClient(clientOptions));
  }

  private List<HttpVersion> testAcceptedVersions(HttpServerOptions serverOptions, HttpClientOptions clientOptions) {
    return testAcceptedVersions(() -> vertx.createHttpServer(serverOptions), () -> vertx.createHttpClient(clientOptions));
  }

  @Test
  public void testDefaultServerConfig() {
    test(
      new HttpServerConfig(),
      new HttpClientConfig(),
      HttpVersion.HTTP_1_1,
      HttpVersion.HTTP_1_0, HttpVersion.HTTP_1_1, HttpVersion.HTTP_2);
    test(
      new HttpServerConfig(),
      new HttpClientConfig().setVersions(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2),
      HttpVersion.HTTP_1_1,
      HttpVersion.HTTP_1_0, HttpVersion.HTTP_1_1, HttpVersion.HTTP_2);
    test(
      new HttpServerConfig(),
      new HttpClientConfig().setVersions(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1),
      HttpVersion.HTTP_2,
      HttpVersion.HTTP_1_0, HttpVersion.HTTP_1_1, HttpVersion.HTTP_2);
    test(
      new HttpServerConfig(),
      new HttpClientConfig().setVersions(HttpVersion.HTTP_2)
        .setHttp2Config(
          new Http2ClientConfig()
            .setClearTextUpgrade(false)),
      HttpVersion.HTTP_2,
      null, null, HttpVersion.HTTP_2);
  }

  @Test
  public void testHttp1ServerConfig() {
    test(
      new HttpServerConfig().setVersions(HttpVersion.HTTP_1_1),
      new HttpClientConfig(),
      HttpVersion.HTTP_1_1,
      HttpVersion.HTTP_1_0, HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1);
    test(
      new HttpServerConfig().setVersions(HttpVersion.HTTP_1_1),
      new HttpClientConfig().setVersions(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2),
      HttpVersion.HTTP_1_1,
      HttpVersion.HTTP_1_0, HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1);
    test(
      new HttpServerConfig().setVersions(HttpVersion.HTTP_1_1),
      new HttpClientConfig().setVersions(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1),
      HttpVersion.HTTP_1_1,
      HttpVersion.HTTP_1_0, HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1);
    test(
      new HttpServerConfig().setVersions(HttpVersion.HTTP_1_1),
      new HttpClientConfig().setVersions(HttpVersion.HTTP_2)
        .setHttp2Config(
          new Http2ClientConfig()
            .setClearTextUpgrade(false)),
      null,
      null, null, null);
  }

  @Test
  public void testHttp2ServerConfig() {
    test(
      new HttpServerConfig().setVersions(HttpVersion.HTTP_2),
      new HttpClientConfig(),
      null,
      null, null, null);
    test(
      new HttpServerConfig().setVersions(HttpVersion.HTTP_2),
      new HttpClientConfig().setVersions(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2),
      null,
      null, null, null);
    test(
      new HttpServerConfig().setVersions(HttpVersion.HTTP_2),
      new HttpClientConfig().setVersions(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1),
      null,
      null, null, null);
    test(
      new HttpServerConfig().setVersions(HttpVersion.HTTP_2),
      new HttpClientConfig().setVersions(HttpVersion.HTTP_2)
        .setHttp2Config(
          new Http2ClientConfig()
            .setClearTextUpgrade(false)),
      HttpVersion.HTTP_2,
      null, null, HttpVersion.HTTP_2);
  }

  @Test
  public void testDefaultTlsServerConfig() {
    test(
      new HttpServerConfig().setSsl(true),
      new HttpClientConfig().setSsl(true),
      HttpVersion.HTTP_2,
      null, HttpVersion.HTTP_1_1, HttpVersion.HTTP_2);
    test(
      new HttpServerConfig().setSsl(true),
      new HttpClientConfig().setSsl(true).setVersions(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2),
      HttpVersion.HTTP_2,
      null, HttpVersion.HTTP_1_1, HttpVersion.HTTP_2);
    test(
      new HttpServerConfig().setSsl(true),
      new HttpClientConfig().setSsl(true).setVersions(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1),
      HttpVersion.HTTP_2,
      null, HttpVersion.HTTP_1_1, HttpVersion.HTTP_2);
    test(
      new HttpServerConfig().setSsl(true),
      new HttpClientConfig().setSsl(true).setVersions(HttpVersion.HTTP_2),
      HttpVersion.HTTP_2,
      null, null, HttpVersion.HTTP_2);
    test(
      new HttpServerConfig().setSsl(true),
      new HttpClientConfig().setSsl(true).setVersions(HttpVersion.HTTP_1_1),
      HttpVersion.HTTP_1_1,
      null, HttpVersion.HTTP_1_1,  null);
  }

  @Test
  public void testHttp1TlsServerConfig() {
    test(
      new HttpServerConfig().setSsl(true).setVersions(HttpVersion.HTTP_1_1),
      new HttpClientConfig().setSsl(true),
      HttpVersion.HTTP_1_1,
      HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1);
    test(
      new HttpServerConfig().setSsl(true).setVersions(HttpVersion.HTTP_1_1),
      new HttpClientConfig().setSsl(true).setVersions(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2),
      HttpVersion.HTTP_1_1,
      HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1);
    test(
      new HttpServerConfig().setSsl(true).setVersions(HttpVersion.HTTP_1_1),
      new HttpClientConfig().setSsl(true).setVersions(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1),
      HttpVersion.HTTP_1_1,
      HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1);
    test(
      new HttpServerConfig().setSsl(true).setVersions(HttpVersion.HTTP_1_1),
      new HttpClientConfig().setSsl(true).setVersions(HttpVersion.HTTP_2),
      null,
      null, null, null);
    test(
      new HttpServerConfig().setSsl(true).setVersions(HttpVersion.HTTP_1_1),
      new HttpClientConfig().setSsl(true).setVersions(HttpVersion.HTTP_1_1),
      HttpVersion.HTTP_1_1,
      HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1, null);
  }

  @Test
  public void testHttp2TlsServerConfig() {
    test(
      new HttpServerConfig().setSsl(true).setVersions(HttpVersion.HTTP_2),
      new HttpClientConfig().setSsl(true),
      HttpVersion.HTTP_2,
      null, null, HttpVersion.HTTP_2);
    test(
      new HttpServerConfig().setSsl(true).setVersions(HttpVersion.HTTP_2),
      new HttpClientConfig().setSsl(true).setVersions(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2),
      HttpVersion.HTTP_2,
      null, null, HttpVersion.HTTP_2);
    test(
      new HttpServerConfig().setSsl(true).setVersions(HttpVersion.HTTP_2),
      new HttpClientConfig().setSsl(true).setVersions(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1),
      HttpVersion.HTTP_2,
      null, null, HttpVersion.HTTP_2);
    test(
      new HttpServerConfig().setSsl(true).setVersions(HttpVersion.HTTP_2),
      new HttpClientConfig().setSsl(true).setVersions(HttpVersion.HTTP_2),
      HttpVersion.HTTP_2,
      null, null, HttpVersion.HTTP_2);
    test(
      new HttpServerConfig().setSsl(true).setVersions(HttpVersion.HTTP_2),
      new HttpClientConfig().setSsl(true).setVersions(HttpVersion.HTTP_1_1),
      null,
      null, null, null);
  }

//
//  @Test
//  public void testConfigH3() {
//    HttpVersion version = configTest(new HttpServerConfig(QUIC_SERVER).setQuicPort(4043), DEFAULT_SERVER_TLS.copy(), new HttpClientConfig(CLIENT_DEFAULT).setVersions(HttpVersion.HTTP_3));
//    assertEquals(HttpVersion.HTTP_3, version);
//  }
//
//  @Test
//  public void testConfigHttp1H3() {
//    HttpVersion version = configTest(new HttpServerOptions(TCP_SERVER_DEFAULT), new HttpServerConfig(QUIC_SERVER).setQuicPort(4043), DEFAULT_SERVER_TLS.copy(), new HttpClientConfig(CLIENT_DEFAULT).setVersions(HttpVersion.HTTP_1_1, HttpVersion.HTTP_3));
//    assertEquals(HttpVersion.HTTP_1_1, version);
//  }
//
//  @Test
//  public void testConfigH2H3() {
//    HttpVersion version = configTest(new HttpServerOptions(TCP_SERVER_DEFAULT_TLS_WITH_ALPN), new HttpServerConfig(QUIC_SERVER).setQuicPort(4043), DEFAULT_SERVER_TLS.copy(), new HttpClientConfig(CLIENT_DEFAULT).setSsl(true).setVersions(HttpVersion.HTTP_2, HttpVersion.HTTP_3));
//    assertEquals(HttpVersion.HTTP_2, version);
//  }
//
//  @Test
//  public void testConfigH2CH3() {
//    HttpVersion version = configTest(new HttpServerOptions(TCP_SERVER_DEFAULT), new HttpServerConfig(QUIC_SERVER).setQuicPort(4043), DEFAULT_SERVER_TLS.copy(), new HttpClientConfig(CLIENT_DEFAULT).setVersions(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1, HttpVersion.HTTP_3));
//    assertEquals(HttpVersion.HTTP_2, version);
//  }
//
//  @Test
//  public void testConfigH2H3NoAlpn() {
//    HttpClientConfig config = new HttpClientConfig(CLIENT_DEFAULT_TLS).setVersions(HttpVersion.HTTP_2, HttpVersion.HTTP_3);
//    configTest(new HttpServerOptions(TCP_SERVER_DEFAULT_TLS_WITH_ALPN), new HttpServerConfig(QUIC_SERVER).setQuicPort(4043), DEFAULT_SERVER_TLS.copy(), config);
//  }

  private void test(HttpServerConfig server, HttpClientConfig client, HttpVersion defaultVersion, HttpVersion... acceptedVersions) {
    test(server, client, true, defaultVersion, acceptedVersions);
  }

  private void test(HttpServerConfig server, HttpClientConfig client, boolean useAlpn, HttpVersion defaultVersion, HttpVersion... acceptedVersions) {
    HttpVersion version = testDefaultVersion(server, client, useAlpn);
    assertEquals(defaultVersion, version);
//    List<HttpVersion> accepted = testAcceptedVersions(server, client, useAlpn);
//    assertEquals(Arrays.asList(acceptedVersions), accepted);
  }

  private HttpVersion testDefaultVersion(HttpServerConfig serverConfig, HttpClientConfig clientConfig, boolean useAlpn) {
    return testDefaultVersion(
      () -> vertx.createHttpServer(serverConfig, useAlpn ? DEFAULT_SERVER_TLS : DEFAULT_SERVER_TLS_NO_ALPN),
      () -> vertx.createHttpClient(clientConfig, DEFAULT_CLIENT_TLS)
    );
  }

  private List<HttpVersion> testAcceptedVersions(HttpServerConfig serverConfig, HttpClientConfig clientConfig, boolean useAlpn) {
    return testAcceptedVersions(
      () -> vertx.createHttpServer(serverConfig, useAlpn ? DEFAULT_SERVER_TLS : DEFAULT_SERVER_TLS_NO_ALPN),
      () -> vertx.createHttpClient(clientConfig, DEFAULT_CLIENT_TLS)
    );
  }

  private HttpVersion testDefaultVersion(Supplier<HttpServer> serverSupplier,  Supplier<HttpClient> clientSupplier) {
    HttpServer server = serverSupplier
      .get()
      .requestHandler(request -> {
        request.response().end();
      });
    try {
      int port = server
        .listen(0)
        .await().actualPort();
      HttpClient client = clientSupplier.get();
      try {
        return client.request(new RequestOptions().setPort(port).setHost("localhost"))
          .compose(request -> request
            .send()
            .expecting(HttpResponseExpectation.SC_OK)
            .compose(HttpClientResponse::end)
            .map(v -> request.version()))
          .await();
      } catch (Exception ignore) {
        return null;
      } finally {
        client.close().await();
      }
    } finally {
      server.close().await();
    }
  }

  private List<HttpVersion> testAcceptedVersions(Supplier<HttpServer> serverSupplier, Supplier<HttpClient> clientSupplier) {
    HttpServer server = serverSupplier
      .get()
      .requestHandler(request -> {
        request.response().end();
      });
    try {
      int port = server
        .listen(0)
        .await().actualPort();
      HttpClient client = clientSupplier.get();
      try {
        List<HttpVersion> result = new ArrayList<>();
        for (HttpVersion test : List.of(HttpVersion.HTTP_1_0, HttpVersion.HTTP_1_1, HttpVersion.HTTP_2)) {
          RequestOptions o = new RequestOptions()
            .setPort(port)
            .setHost("localhost")
            .setProtocolVersion(test); // Should be named set version to match HttpClientConfig
          try {
            HttpVersion version = client.request(o)
              .compose(request -> request
                .send()
                .expecting(HttpResponseExpectation.SC_OK)
                .compose(HttpClientResponse::end)
                .map(v -> request.version()))
              .await();
            result.add(version);
          } catch (Exception e) {
            result.add((null));
          }
        }
        return result;
      } finally {
        client.close().await();
      }
    } finally {
      server.close().await();
    }
  }
}
