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
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.ProxyOptions;
import io.vertx.test.http.HttpTestBase;
import io.vertx.test.proxy.Proxy;
import io.vertx.test.proxy.ProxyKind;
import io.vertx.test.proxy.WithProxy;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Rule;
import org.junit.Test;

/**
 * The invariant: {@code connection.isSsl()} must be identical whether a connection is direct, via an
 * HTTP proxy, or via an HTTPS proxy. The proxy is a transport detail and must not change
 * {@code isSsl()} — it reports whether the ORIGIN connection is encrypted.
 *
 * <p>Each test starts one origin, then opens a connection direct and a connection through the proxy of
 * the method's {@link WithProxy} kind, and asserts the two {@code isSsl()} values are equal.
 * {@code @WithProxy} selects one proxy kind per method, so there are two tests per case (HTTP proxy and
 * HTTPS proxy). Origins are cleaned up by {@code tearDown} (vertx close), like other {@link HttpTestBase}
 * tests; the per-type client logic ({@link #connectNetSocket}, {@link #connectHttp},
 * {@link #connectWebSocket}) is reused across cases.
 *
 * <p>Covers the four main proxy-capable connection types (NetSocket, HTTP/1, HTTP/2, WebSocket)
 * against a plain and a TLS origin.
 */
public class ProxyIsSslConsistencyTest extends HttpTestBase {

  // CONNECT-safe origin port: >= 1024 and != DEFAULT_HTTP_PORT (8080), which HttpProxy denies for CONNECT.
  private static final int ORIGIN_PORT = DEFAULT_HTTPS_PORT;
  private static final String ORIGIN_HOST = "localhost";

  @Rule
  public Proxy proxy = new Proxy();

  // --- Origins (one per test; closed by tearDown) ---------------------------

  private void startNetOrigin(boolean originTls) throws Exception {
    NetServerOptions options = new NetServerOptions().setPort(ORIGIN_PORT).setHost(ORIGIN_HOST);
    if (originTls) {
      options.setSsl(true).setKeyCertOptions(Cert.SERVER_JKS.get());
    }
    vertx.createNetServer(options).connectHandler(so -> {}).listen().await();
  }

  private void startHttpOrigin(HttpVersion version, boolean originTls) throws Exception {
    HttpServerOptions options = new HttpServerOptions().setPort(ORIGIN_PORT).setHost(ORIGIN_HOST);
    if (originTls) {
      options.setSsl(true).setKeyCertOptions(Cert.SERVER_JKS.get()).setUseAlpn(version == HttpVersion.HTTP_2);
    }
    createHttpServer(options).requestHandler(req -> req.response().end("ok")).listen().await();
  }

  private void startWebSocketOrigin(boolean originTls) throws Exception {
    HttpServerOptions options = new HttpServerOptions().setPort(ORIGIN_PORT).setHost(ORIGIN_HOST);
    if (originTls) {
      options.setSsl(true).setKeyCertOptions(Cert.SERVER_JKS.get());
    }
    createHttpServer(options).webSocketHandler(ws -> ws.handler(buff -> {})).listen().await();
  }

  // --- Reusable per-type client logic: connect (direct if proxyOptions is null, else through the
  //     proxy) and return connection.isSsl() ------------------------------------------------------

  private boolean connectNetSocket(boolean originTls, ProxyOptions proxyOptions) throws Exception {
    NetClientOptions options = new NetClientOptions();
    if (originTls) {
      options.setSsl(true).setTrustOptions(Trust.SERVER_JKS.get()).setHostnameVerificationAlgorithm("HTTPS");
    }
    if (proxyOptions != null) {
      options.setProxyOptions(proxyOptions);
    }
    NetClient client = vertx.createNetClient(options);
    try {
      NetSocket so = client.connect(ORIGIN_PORT, ORIGIN_HOST).await();
      return so.isSsl();
    } finally {
      client.close().await();
    }
  }

  private boolean connectHttp(HttpVersion version, boolean originTls, ProxyOptions proxyOptions) throws Exception {
    HttpClientOptions options = new HttpClientOptions().setProtocolVersion(version);
    if (originTls) {
      options.setSsl(true).setTrustOptions(Trust.SERVER_JKS.get()).setUseAlpn(version == HttpVersion.HTTP_2);
    }
    if (proxyOptions != null) {
      options.setProxyOptions(proxyOptions);
    }
    HttpClient client = createHttpClient(options);
    try {
      return client.request(new RequestOptions().setHost(ORIGIN_HOST).setPort(ORIGIN_PORT).setURI("/"))
        .compose(req -> req.send().map(resp -> resp.request().connection().isSsl()))
        .await();
    } finally {
      client.close().await();
    }
  }

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
    assertEquals(isSslDirect, isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void netSocket_plainOrigin_httpsProxy() throws Exception {
    startNetOrigin(false);
    boolean isSslDirect = connectNetSocket(false, null);
    boolean isSslViaProxy = connectNetSocket(false, proxy.options());
    assertEquals(isSslDirect, isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTP)
  public void netSocket_tlsOrigin_httpProxy() throws Exception {
    startNetOrigin(true);
    boolean isSslDirect = connectNetSocket(true, null);
    boolean isSslViaProxy = connectNetSocket(true, proxy.options());
    assertEquals(isSslDirect, isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void netSocket_tlsOrigin_httpsProxy() throws Exception {
    startNetOrigin(true);
    boolean isSslDirect = connectNetSocket(true, null);
    boolean isSslViaProxy = connectNetSocket(true, proxy.options());
    assertEquals(isSslDirect, isSslViaProxy);
  }

  // --- HTTP/1 ---------------------------------------------------------------

  @Test
  @WithProxy(kind = ProxyKind.HTTP)
  public void http1_plainOrigin_httpProxy() throws Exception {
    startHttpOrigin(HttpVersion.HTTP_1_1, false);
    boolean isSslDirect = connectHttp(HttpVersion.HTTP_1_1, false, null);
    boolean isSslViaProxy = connectHttp(HttpVersion.HTTP_1_1, false, proxy.options());
    assertEquals(isSslDirect, isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void http1_plainOrigin_httpsProxy() throws Exception {
    startHttpOrigin(HttpVersion.HTTP_1_1, false);
    boolean isSslDirect = connectHttp(HttpVersion.HTTP_1_1, false, null);
    boolean isSslViaProxy = connectHttp(HttpVersion.HTTP_1_1, false, proxy.options());
    assertEquals(isSslDirect, isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTP)
  public void http1_tlsOrigin_httpProxy() throws Exception {
    startHttpOrigin(HttpVersion.HTTP_1_1, true);
    boolean isSslDirect = connectHttp(HttpVersion.HTTP_1_1, true, null);
    boolean isSslViaProxy = connectHttp(HttpVersion.HTTP_1_1, true, proxy.options());
    assertEquals(isSslDirect, isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void http1_tlsOrigin_httpsProxy() throws Exception {
    startHttpOrigin(HttpVersion.HTTP_1_1, true);
    boolean isSslDirect = connectHttp(HttpVersion.HTTP_1_1, true, null);
    boolean isSslViaProxy = connectHttp(HttpVersion.HTTP_1_1, true, proxy.options());
    assertEquals(isSslDirect, isSslViaProxy);
  }

  // --- HTTP/2 ---------------------------------------------------------------

  @Test
  @WithProxy(kind = ProxyKind.HTTP)
  public void http2_plainOrigin_httpProxy() throws Exception {
    startHttpOrigin(HttpVersion.HTTP_2, false);
    boolean isSslDirect = connectHttp(HttpVersion.HTTP_2, false, null);
    boolean isSslViaProxy = connectHttp(HttpVersion.HTTP_2, false, proxy.options());
    assertEquals(isSslDirect, isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void http2_plainOrigin_httpsProxy() throws Exception {
    startHttpOrigin(HttpVersion.HTTP_2, false);
    boolean isSslDirect = connectHttp(HttpVersion.HTTP_2, false, null);
    boolean isSslViaProxy = connectHttp(HttpVersion.HTTP_2, false, proxy.options());
    assertEquals(isSslDirect, isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTP)
  public void http2_tlsOrigin_httpProxy() throws Exception {
    startHttpOrigin(HttpVersion.HTTP_2, true);
    boolean isSslDirect = connectHttp(HttpVersion.HTTP_2, true, null);
    boolean isSslViaProxy = connectHttp(HttpVersion.HTTP_2, true, proxy.options());
    assertEquals(isSslDirect, isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void http2_tlsOrigin_httpsProxy() throws Exception {
    startHttpOrigin(HttpVersion.HTTP_2, true);
    boolean isSslDirect = connectHttp(HttpVersion.HTTP_2, true, null);
    boolean isSslViaProxy = connectHttp(HttpVersion.HTTP_2, true, proxy.options());
    assertEquals(isSslDirect, isSslViaProxy);
  }

  // --- WebSocket ------------------------------------------------------------

  @Test
  @WithProxy(kind = ProxyKind.HTTP)
  public void webSocket_plainOrigin_httpProxy() throws Exception {
    startWebSocketOrigin(false);
    boolean isSslDirect = connectWebSocket(false, null);
    boolean isSslViaProxy = connectWebSocket(false, proxy.options());
    assertEquals(isSslDirect, isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void webSocket_plainOrigin_httpsProxy() throws Exception {
    startWebSocketOrigin(false);
    boolean isSslDirect = connectWebSocket(false, null);
    boolean isSslViaProxy = connectWebSocket(false, proxy.options());
    assertEquals(isSslDirect, isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTP)
  public void webSocket_tlsOrigin_httpProxy() throws Exception {
    startWebSocketOrigin(true);
    boolean isSslDirect = connectWebSocket(true, null);
    boolean isSslViaProxy = connectWebSocket(true, proxy.options());
    assertEquals(isSslDirect, isSslViaProxy);
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void webSocket_tlsOrigin_httpsProxy() throws Exception {
    startWebSocketOrigin(true);
    boolean isSslDirect = connectWebSocket(true, null);
    boolean isSslViaProxy = connectWebSocket(true, proxy.options());
    assertEquals(isSslDirect, isSslViaProxy);
  }
}
