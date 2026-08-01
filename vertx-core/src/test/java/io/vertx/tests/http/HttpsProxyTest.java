/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http;

import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;

import io.vertx.core.http.HttpClientConfig;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.http.HttpServerConfig;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.TcpClientConfig;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpTestBase2;
import io.vertx.test.proxy.Proxy;
import io.vertx.test.proxy.ProxyKind;
import io.vertx.test.proxy.WithProxy;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Rule;
import org.junit.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests for {@link ProxyType#HTTPS}: the connection to the proxy itself (leg 1) is established over
 * TLS. The proxying semantics (absolute-URI {@code GET} forwarding for plain origins, {@code CONNECT}
 * tunnel for TLS origins) are unchanged from {@link ProxyType#HTTP}.
 */
public class HttpsProxyTest extends HttpTestBase2 {

  @Rule
  public Proxy proxy = new Proxy();

  private NetServer netOrigin;

  @Override
  protected void tearDown() throws Exception {
    if (netOrigin != null) {
      netOrigin.close().await();
      netOrigin = null;
    }
    if (server != null) {
      server.close().await();
    }
    super.tearDown();
  }

  // The origin echoes the TLS status of its own (leg 2) connection, so that tests assert the origin view
  // and not only the client one: leg 1 being TLS must not make a plain origin look encrypted, and a
  // tunnelled TLS origin must still see its own TLS.

  private void startHttpOrigin() throws Exception {
    server.requestHandler(req -> req.response().end("Hello from origin ssl=" + req.isSSL()));
    startServer();
  }

  private void startHttpsOrigin(HttpVersion version) throws Exception {
    server = vertx.createHttpServer(
      new HttpServerConfig().setPort(DEFAULT_HTTPS_PORT).setHost(DEFAULT_HTTPS_HOST),
      new ServerSSLOptions()
        .setKeyCertOptions(Cert.SERVER_JKS.get())
        .setUseAlpn(version == HttpVersion.HTTP_2));
    server.requestHandler(req -> req.response().end("Hello from origin ssl=" + req.isSSL()));
    startServer(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST));
  }

  /** Default proxy SSL options trusting the proxy's (localhost) certificate. */
  private static ClientSSLOptions trustingProxySsl() {
    return new ClientSSLOptions().setTrustOptions(Trust.SERVER_JKS.get());
  }

  /** A client config whose transport routes through {@code proxyOptions}. */
  private static HttpClientConfig proxiedConfig(ProxyOptions proxyOptions) {
    HttpClientConfig config = new HttpClientConfig();
    config.getTcpConfig().setProxyOptions(proxyOptions);
    return config;
  }

  // --- Happy paths ----------------------------------------------------------

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void testHttpsProxy_HttpOrigin_forwarded() throws Exception {
    startHttpOrigin();

    client.close();
    client = vertx.createHttpClient(proxiedConfig(proxy.options()));

    Buffer body = client.request(new RequestOptions().setMethod(HttpMethod.GET)
        .setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT).setURI("/"))
      .compose(req -> req.send().expecting(HttpResponseExpectation.SC_OK))
      .compose(HttpClientResponse::body)
      .await();

    assertEquals("Hello from origin ssl=false", body.toString());
    // Plain origin: the proxy GET-forwards (it sees the absolute URI), it does not CONNECT.
    assertEquals(HttpMethod.GET, proxy.lastMethod());
    assertNotNull(proxy.lastUri());
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void testHttpsProxy_HttpsOrigin_tunnelled() throws Exception {
    startHttpsOrigin(HttpVersion.HTTP_1_1);

    client.close();
    client = vertx.createHttpClient(
      proxiedConfig(proxy.options()).setSsl(true),
      new ClientSSLOptions().setTrustOptions(Trust.SERVER_JKS.get()));

    Buffer body = client.request(new RequestOptions().setMethod(HttpMethod.GET)
        .setHost(DEFAULT_HTTPS_HOST).setPort(DEFAULT_HTTPS_PORT).setURI("/"))
      .compose(req -> req.send().expecting(HttpResponseExpectation.SC_OK))
      .compose(HttpClientResponse::body)
      .await();

    assertEquals("Hello from origin ssl=true", body.toString());
    // TLS origin: nested TLS through the CONNECT tunnel (the proxy never sees the encrypted request).
    assertEquals(HttpMethod.CONNECT, proxy.lastMethod());
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void testHttpsProxy_Http2Origin_tunnelled() throws Exception {
    startHttpsOrigin(HttpVersion.HTTP_2);

    client.close();
    client = vertx.createHttpClient(
      proxiedConfig(proxy.options()).setVersions(HttpVersion.HTTP_2).setSsl(true),
      new ClientSSLOptions().setTrustOptions(Trust.SERVER_JKS.get()).setUseAlpn(true));

    String result = client.request(new RequestOptions().setMethod(HttpMethod.GET)
        .setHost(DEFAULT_HTTPS_HOST).setPort(DEFAULT_HTTPS_PORT).setURI("/"))
      .compose(req -> req.send().expecting(HttpResponseExpectation.SC_OK))
      .compose(resp -> resp.body().map(body -> resp.version() + ":" + body))
      .await();

    // h2 rides the CONNECT tunnel for free; ALPN negotiates h2 end-to-end with the origin.
    assertEquals(HttpVersion.HTTP_2 + ":Hello from origin ssl=true", result);
    assertEquals(HttpMethod.CONNECT, proxy.lastMethod());
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS, username = "user1")
  public void testHttpsProxy_proxyAuthentication() throws Exception {
    startHttpOrigin();

    client.close();
    client = vertx.createHttpClient(proxiedConfig(proxy.options().setUsername("user1").setPassword("user1")));

    Buffer body = client.request(new RequestOptions().setMethod(HttpMethod.GET)
        .setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT).setURI("/"))
      .compose(req -> req.send().expecting(HttpResponseExpectation.SC_OK))
      .compose(HttpClientResponse::body)
      .await();

    // SC_OK proves the Basic Proxy-Authorization (sent inside the established leg-1 TLS) was accepted;
    // the proxy returns 407 on missing/incorrect credentials.
    assertEquals("Hello from origin ssl=false", body.toString());
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS, requireSslClientAuth = true)
  public void testHttpsProxy_mutualTls() throws Exception {
    startHttpOrigin();

    client.close();
    // The proxy requires a client certificate (server-side); the client presents one here (client-side).
    client = vertx.createHttpClient(proxiedConfig(proxy.options()
      .setSslOptions(new ClientSSLOptions()
        .setTrustOptions(Trust.SERVER_JKS.get())
        .setKeyCertOptions(Cert.CLIENT_JKS.get()))));

    Buffer body = client.request(new RequestOptions().setMethod(HttpMethod.GET)
        .setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT).setURI("/"))
      .compose(req -> req.send().expecting(HttpResponseExpectation.SC_OK))
      .compose(HttpClientResponse::body)
      .await();

    assertEquals("Hello from origin ssl=false", body.toString());
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void testHttpsProxy_sendFileThroughTunnel() throws Exception {
    // A client NetSocket.sendFile() to a plain origin through the HTTPS proxy: the file must travel
    // through the leg-1 TLS (the "proxy-ssl" SslHandler on the client pipeline). Because the origin is
    // plain, the connection's isSsl() is false, so the old !isSsl() zero-copy check would send the raw
    // file bytes bypassing the SslHandler and break the TLS tunnel. supportsFileRegion() must instead
    // detect the SslHandler and fall back to chunked writes.
    String content = TestUtils.randomUnicodeString(10000);
    File file = Files.createTempFile("vertx", ".txt").toFile();
    file.deleteOnExit();
    Files.write(file.toPath(), content.getBytes("UTF-8"));
    Buffer expected = Buffer.buffer(content);

    // Plain origin. Not DEFAULT_HTTP_PORT: the proxy denies CONNECT to that port.
    Buffer received = Buffer.buffer();
    Promise<Void> allReceived = Promise.promise();
    netOrigin = vertx.createNetServer();
    netOrigin.connectHandler(sock -> sock.handler(buff -> {
      received.appendBuffer(buff);
      if (received.length() == expected.length()) {
        allReceived.complete();
      }
    }));
    netOrigin.listen(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST).await();

    NetClient netClient = vertx.createNetClient(new TcpClientConfig().setProxyOptions(proxy.options()));
    NetSocket sock = netClient.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST).await();
    sock.sendFile(file.getAbsolutePath()).await();

    allReceived.future().await();
    assertEquals(expected, received);
    assertEquals(HttpMethod.CONNECT, proxy.lastMethod());
  }

  // --- Security / failure cases ---------------------------------------------

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void testHttpsProxy_untrustedProxyCertRejected() throws Exception {
    startHttpOrigin();

    client.close();
    // No sslOptions => default trust source (no self-signed cert): the proxy cert is not trusted and
    // there is no plaintext fallback for an HTTPS proxy.
    client = vertx.createHttpClient(proxiedConfig(
      new ProxyOptions().setType(ProxyType.HTTPS).setHost("localhost").setPort(proxy.port())));

    // connect must fail rather than hanging or silently downgrading
    assertThatThrownBy(() -> client.request(new RequestOptions().setMethod(HttpMethod.GET)
        .setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT).setURI("/"))
      .compose(req -> req.send())
      .await())
      .isInstanceOf(SSLHandshakeException.class)
      .hasStackTraceContaining("unable to find valid certification path");
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTPS)
  public void testHttpsProxy_hostnameMismatchRejected() throws Exception {
    startHttpOrigin();

    client.close();
    // Proxy cert is CN=localhost; connecting via 127.0.0.1 must fail hostname verification (on by default).
    client = vertx.createHttpClient(proxiedConfig(
      new ProxyOptions().setType(ProxyType.HTTPS).setHost("127.0.0.1").setPort(proxy.port())
        .setSslOptions(trustingProxySsl())));

    // verification must reject the mismatched proxy hostname
    assertThatThrownBy(() -> client.request(new RequestOptions().setMethod(HttpMethod.GET)
        .setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT).setURI("/"))
      .compose(req -> req.send())
      .await())
      .isInstanceOf(SSLHandshakeException.class)
      .hasStackTraceContaining("No subject alternative names present");
  }

  @Test
  @WithProxy(kind = ProxyKind.HTTP)
  public void testHttpsProxy_againstPlaintextProxyFailsCleanly() throws Exception {
    startHttpOrigin();

    client.close();
    // A HTTPS proxy type pointed at a plaintext proxy: no silent downgrade, the TLS handshake must fail.
    client = vertx.createHttpClient(proxiedConfig(
      new ProxyOptions().setType(ProxyType.HTTPS).setHost("localhost").setPort(proxy.port())
        .setSslOptions(trustingProxySsl())));

    assertThatThrownBy(() -> client.request(new RequestOptions().setMethod(HttpMethod.GET)
        .setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT).setURI("/"))
      .compose(req -> req.send())
      .await())
      .isInstanceOf(SSLHandshakeException.class)
      .hasStackTraceContaining("not an SSL/TLS record");
  }
}
