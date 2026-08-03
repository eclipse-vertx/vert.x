/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientConfig;
import io.vertx.core.http.HttpServerConfig;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.net.TcpClientConfig;
import io.vertx.core.net.TcpServerConfig;
import io.vertx.test.http.HttpTestBase2;
import io.vertx.test.proxy.Proxy;
import io.vertx.test.proxy.ProxyKind;
import io.vertx.test.proxy.WithProxy;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The invariant: {@code connection.isSsl()} reports whether the ORIGIN connection is encrypted, so it
 * must equal the origin's TLS setting whether the connection is direct, via an HTTP proxy, or via an
 * HTTPS proxy. The proxy is a transport detail and must not change {@code isSsl()} — in particular the
 * TLS leg to an HTTPS proxy must not make a plaintext origin look encrypted.
 *
 * <p>Each test starts one origin, then opens a connection direct and a connection through the proxy of
 * the method's {@link WithProxy} kind, and asserts that both report the origin's TLS setting. The direct
 * connection is the control: on failure it tells a proxy regression apart from one that was already
 * broken without a proxy. {@code @WithProxy} selects one proxy kind per method, so there are two tests
 * per case (HTTP proxy and HTTPS proxy). Origins are cleaned up by {@code tearDown} (vertx close); the
 * per-type client logic ({@link #connectNetSocket}, {@link #connectHttp}, {@link #connectWebSocket}) is
 * reused across cases.
 *
 * <p>Covers the four main proxy-capable connection types (NetSocket, HTTP/1, HTTP/2, WebSocket)
 * against a plain and a TLS origin.
 */
public class ProxyIsSslConsistencyTest extends HttpTestBase2 {

  // CONNECT-safe origin port: >= 1024 and != DEFAULT_HTTP_PORT (8080), which HttpProxy denies for CONNECT.
  private static final int ORIGIN_PORT = DEFAULT_HTTPS_PORT;
  private static final String ORIGIN_HOST = "localhost";

  @Rule
  public Proxy proxy = new Proxy();

  private NetServer netOrigin;
  private HttpServer httpOrigin;

  @Override
  protected void tearDown() throws Exception {
    if (netOrigin != null) {
      netOrigin.close().await();
      netOrigin = null;
    }
    if (httpOrigin != null) {
      httpOrigin.close().await();
      httpOrigin = null;
    }
    super.tearDown();
  }

  // --- Origins (one per test; closed by tearDown) ---------------------------

  private void startNetOrigin(boolean originTls) throws Exception {
    TcpServerConfig config = new TcpServerConfig()
      .setPort(ORIGIN_PORT)
      .setHost(ORIGIN_HOST)
      .setSsl(originTls);
    ServerSSLOptions sslOptions = originTls
      ? new ServerSSLOptions().setKeyCertOptions(Cert.SERVER_JKS.get())
      : null;
    netOrigin = vertx.createNetServer(config, sslOptions).connectHandler(so -> {});
    netOrigin.listen().await();
  }

  private void startHttpOrigin(HttpVersion version, boolean originTls) throws Exception {
    HttpServerConfig config = new HttpServerConfig().setPort(ORIGIN_PORT).setHost(ORIGIN_HOST);
    ServerSSLOptions sslOptions = originTls
      ? new ServerSSLOptions()
      .setKeyCertOptions(Cert.SERVER_JKS.get())
      .setUseAlpn(version == HttpVersion.HTTP_2)
      : null;
    httpOrigin = vertx.createHttpServer(config, sslOptions).requestHandler(req -> req.response().end("ok"));
    httpOrigin.listen().await();
  }

  private void startWebSocketOrigin(boolean originTls) throws Exception {
    HttpServerConfig config = new HttpServerConfig().setPort(ORIGIN_PORT).setHost(ORIGIN_HOST);
    ServerSSLOptions sslOptions = originTls
      ? new ServerSSLOptions().setKeyCertOptions(Cert.SERVER_JKS.get())
      : null;
    httpOrigin = vertx.createHttpServer(config, sslOptions).webSocketHandler(ws -> ws.handler(buff -> {}));
    httpOrigin.listen().await();
  }

  // --- Reusable per-type client logic: connect (direct if proxyOptions is null, else through the
  //     proxy) and return connection.isSsl() ------------------------------------------------------

  private boolean connectNetSocket(boolean originTls, ProxyOptions proxyOptions) throws Exception {
    TcpClientConfig config = new TcpClientConfig().setSsl(originTls);
    if (proxyOptions != null) {
      config.setProxyOptions(proxyOptions);
    }
    ClientSSLOptions sslOptions = originTls
      ? new ClientSSLOptions()
      .setTrustOptions(Trust.SERVER_JKS.get())
      .setHostnameVerificationAlgorithm("HTTPS")
      : null;
    NetClient client = vertx.createNetClient(config, sslOptions);
    try {
      NetSocket so = client.connect(ORIGIN_PORT, ORIGIN_HOST).await();
      return so.isSsl();
    } finally {
      client.close().await();
    }
  }

  private boolean connectHttp(HttpVersion version, boolean originTls, ProxyOptions proxyOptions) throws Exception {
    HttpClientConfig config = new HttpClientConfig().setSsl(originTls);
    if (version == HttpVersion.HTTP_2 && !originTls) {
      // h2c starts as an HTTP/1.1 upgrade, so the client must support HTTP/1.1 as well
      config.setVersions(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1);
    } else {
      config.setVersions(version);
    }
    if (proxyOptions != null) {
      config.getTcpConfig().setProxyOptions(proxyOptions);
    }
    ClientSSLOptions sslOptions = originTls
      ? new ClientSSLOptions()
      .setTrustOptions(Trust.SERVER_JKS.get())
      .setUseAlpn(version == HttpVersion.HTTP_2)
      : null;
    HttpClient client = vertx.createHttpClient(config, sslOptions);
    try {
      return client.request(new RequestOptions().setHost(ORIGIN_HOST).setPort(ORIGIN_PORT).setURI("/"))
        .compose(req -> req.send().map(resp -> resp.request().connection().isSsl()))
        .await();
    } finally {
      client.close().await();
    }
  }

  // WebSocketClient has no config counterpart yet, so this one stays on the options class.
  private boolean connectWebSocket(boolean originTls, ProxyOptions proxyOptions) throws Exception {
    WebSocketClientOptions options = new WebSocketClientOptions();
    if (originTls) {
      options.setSsl(true).setTrustOptions(Trust.SERVER_JKS.get());
    }
    if (proxyOptions != null) {
      options.setProxyOptions(proxyOptions);
    }
    WebSocketClient client = vertx.createWebSocketClient(options);
    try {
      WebSocket ws = client.connect(new WebSocketConnectOptions()
        .setHost(ORIGIN_HOST).setPort(ORIGIN_PORT).setURI("/").setSsl(originTls)).await();
      return ws.isSsl();
    } finally {
      client.close().await();
    }
  }

  // --- NetSocket ------------------------------------------------------------

  @Test
  @WithProxy(kind = ProxyKind.HTTP)
  public void netSocket_plainOrigin_httpProxy() throws Exception {
    startNetOrigin(false);
    boolean isSslDirect = connectNetSocket(false, null);
    boolean isSslViaProxy = connectNetSocket(false, proxy.options());
    assertFalse(isSslDirect);
    assertFalse(isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void netSocket_plainOrigin_httpsProxy() throws Exception {
    startNetOrigin(false);
    boolean isSslDirect = connectNetSocket(false, null);
    boolean isSslViaProxy = connectNetSocket(false, proxy.options());
    assertFalse(isSslDirect);
    assertFalse(isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTP)
  public void netSocket_tlsOrigin_httpProxy() throws Exception {
    startNetOrigin(true);
    boolean isSslDirect = connectNetSocket(true, null);
    boolean isSslViaProxy = connectNetSocket(true, proxy.options());
    assertTrue(isSslDirect);
    assertTrue(isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void netSocket_tlsOrigin_httpsProxy() throws Exception {
    startNetOrigin(true);
    boolean isSslDirect = connectNetSocket(true, null);
    boolean isSslViaProxy = connectNetSocket(true, proxy.options());
    assertTrue(isSslDirect);
    assertTrue(isSslViaProxy);
  }

  // --- HTTP/1 ---------------------------------------------------------------

  @Test
  @WithProxy(kind = ProxyKind.HTTP)
  public void http1_plainOrigin_httpProxy() throws Exception {
    startHttpOrigin(HttpVersion.HTTP_1_1, false);
    boolean isSslDirect = connectHttp(HttpVersion.HTTP_1_1, false, null);
    boolean isSslViaProxy = connectHttp(HttpVersion.HTTP_1_1, false, proxy.options());
    assertFalse(isSslDirect);
    assertFalse(isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void http1_plainOrigin_httpsProxy() throws Exception {
    startHttpOrigin(HttpVersion.HTTP_1_1, false);
    boolean isSslDirect = connectHttp(HttpVersion.HTTP_1_1, false, null);
    boolean isSslViaProxy = connectHttp(HttpVersion.HTTP_1_1, false, proxy.options());
    assertFalse(isSslDirect);
    assertFalse(isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTP)
  public void http1_tlsOrigin_httpProxy() throws Exception {
    startHttpOrigin(HttpVersion.HTTP_1_1, true);
    boolean isSslDirect = connectHttp(HttpVersion.HTTP_1_1, true, null);
    boolean isSslViaProxy = connectHttp(HttpVersion.HTTP_1_1, true, proxy.options());
    assertTrue(isSslDirect);
    assertTrue(isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void http1_tlsOrigin_httpsProxy() throws Exception {
    startHttpOrigin(HttpVersion.HTTP_1_1, true);
    boolean isSslDirect = connectHttp(HttpVersion.HTTP_1_1, true, null);
    boolean isSslViaProxy = connectHttp(HttpVersion.HTTP_1_1, true, proxy.options());
    assertTrue(isSslDirect);
    assertTrue(isSslViaProxy);
  }

  // --- HTTP/2 ---------------------------------------------------------------

  @Test
  @WithProxy(kind = ProxyKind.HTTP)
  public void http2_plainOrigin_httpProxy() throws Exception {
    startHttpOrigin(HttpVersion.HTTP_2, false);
    boolean isSslDirect = connectHttp(HttpVersion.HTTP_2, false, null);
    boolean isSslViaProxy = connectHttp(HttpVersion.HTTP_2, false, proxy.options());
    assertFalse(isSslDirect);
    assertFalse(isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void http2_plainOrigin_httpsProxy() throws Exception {
    startHttpOrigin(HttpVersion.HTTP_2, false);
    boolean isSslDirect = connectHttp(HttpVersion.HTTP_2, false, null);
    boolean isSslViaProxy = connectHttp(HttpVersion.HTTP_2, false, proxy.options());
    assertFalse(isSslDirect);
    assertFalse(isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTP)
  public void http2_tlsOrigin_httpProxy() throws Exception {
    startHttpOrigin(HttpVersion.HTTP_2, true);
    boolean isSslDirect = connectHttp(HttpVersion.HTTP_2, true, null);
    boolean isSslViaProxy = connectHttp(HttpVersion.HTTP_2, true, proxy.options());
    assertTrue(isSslDirect);
    assertTrue(isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void http2_tlsOrigin_httpsProxy() throws Exception {
    startHttpOrigin(HttpVersion.HTTP_2, true);
    boolean isSslDirect = connectHttp(HttpVersion.HTTP_2, true, null);
    boolean isSslViaProxy = connectHttp(HttpVersion.HTTP_2, true, proxy.options());
    assertTrue(isSslDirect);
    assertTrue(isSslViaProxy);
  }

  // --- WebSocket ------------------------------------------------------------

  @Test
  @WithProxy(kind = ProxyKind.HTTP)
  public void webSocket_plainOrigin_httpProxy() throws Exception {
    startWebSocketOrigin(false);
    boolean isSslDirect = connectWebSocket(false, null);
    boolean isSslViaProxy = connectWebSocket(false, proxy.options());
    assertFalse(isSslDirect);
    assertFalse(isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void webSocket_plainOrigin_httpsProxy() throws Exception {
    startWebSocketOrigin(false);
    boolean isSslDirect = connectWebSocket(false, null);
    boolean isSslViaProxy = connectWebSocket(false, proxy.options());
    assertFalse(isSslDirect);
    assertFalse(isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTP)
  public void webSocket_tlsOrigin_httpProxy() throws Exception {
    startWebSocketOrigin(true);
    boolean isSslDirect = connectWebSocket(true, null);
    boolean isSslViaProxy = connectWebSocket(true, proxy.options());
    assertTrue(isSslDirect);
    assertTrue(isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void webSocket_tlsOrigin_httpsProxy() throws Exception {
    startWebSocketOrigin(true);
    boolean isSslDirect = connectWebSocket(true, null);
    boolean isSslViaProxy = connectWebSocket(true, proxy.options());
    assertTrue(isSslDirect);
    assertTrue(isSslViaProxy);
  }
}
