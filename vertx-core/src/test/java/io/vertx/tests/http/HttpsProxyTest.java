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

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.http.HttpTestBase;
import io.vertx.test.proxy.HttpProxy;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Test;

import javax.net.ssl.SSLHandshakeException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ProxyType#HTTPS}: the connection to the proxy itself (leg 1) is established over
 * TLS. The proxying semantics (absolute-URI {@code GET} forwarding for plain origins, {@code CONNECT}
 * tunnel for TLS origins) are unchanged from {@link ProxyType#HTTP}.
 */
public class HttpsProxyTest extends HttpTestBase {

  private Vertx proxyVertx;
  private HttpProxy proxy;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // The proxy runs on its own Vertx instance so that closing it in tearDown awaits the release of
    // its listen socket. HttpProxy.stop() alone does not wait for the close to complete, which leaks
    // the port into the following tests.
    proxyVertx = Vertx.vertx();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (proxy != null) {
        proxy.stop();
      }
    } finally {
      if (proxyVertx != null) {
        proxyVertx.close().await();
      }
    }
    super.tearDown();
  }

  private void startHttpOrigin() throws Exception {
    server.requestHandler(req -> req.response().end("Hello from origin"));
    startServer();
  }

  private void startHttpsOrigin(HttpVersion version) throws Exception {
    server = vertx.createHttpServer(createBaseServerOptions()
      .setSsl(true)
      .setUseAlpn(version == HttpVersion.HTTP_2)
      .setKeyCertOptions(Cert.SERVER_JKS.get()));
    server.requestHandler(req -> req.response().end("Hello from origin"));
    startServer(SocketAddress.inetSocketAddress(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST));
  }

  /** Default proxy SSL options trusting the proxy's (localhost) certificate. */
  private static ClientSSLOptions trustingProxySsl() {
    return new ClientSSLOptions().setTrustOptions(Trust.SERVER_JKS.get());
  }

  private static ProxyOptions httpsProxy(int port) {
    return new ProxyOptions()
      .setType(ProxyType.HTTPS)
      .setHost("localhost")
      .setPort(port)
      .setSslOptions(trustingProxySsl());
  }

  // --- Happy paths ----------------------------------------------------------

  @Test
  public void testHttpsProxy_HttpOrigin_forwarded() throws Exception {
    proxy = new HttpProxy().ssl(Cert.SERVER_JKS.get());
    proxy.start(proxyVertx);
    startHttpOrigin();

    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setProxyOptions(httpsProxy(proxy.port())));

    Buffer body = client.request(new RequestOptions().setMethod(HttpMethod.GET)
        .setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT).setURI("/"))
      .compose(req -> req.send().expecting(HttpResponseExpectation.SC_OK))
      .compose(HttpClientResponse::body)
      .await();

    assertEquals("Hello from origin", body.toString());
    // Plain origin: the proxy GET-forwards (it sees the absolute URI), it does not CONNECT.
    assertEquals(HttpMethod.GET, proxy.getLastMethod());
    assertNotNull(proxy.getLastUri());
  }

  @Test
  public void testHttpsProxy_HttpsOrigin_tunnelled() throws Exception {
    proxy = new HttpProxy().ssl(Cert.SERVER_JKS.get());
    proxy.start(proxyVertx);
    startHttpsOrigin(HttpVersion.HTTP_1_1);

    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
      .setSsl(true)
      .setTrustOptions(Trust.SERVER_JKS.get())
      .setProxyOptions(httpsProxy(proxy.port())));

    Buffer body = client.request(new RequestOptions().setMethod(HttpMethod.GET)
        .setHost(DEFAULT_HTTPS_HOST).setPort(DEFAULT_HTTPS_PORT).setURI("/"))
      .compose(req -> req.send().expecting(HttpResponseExpectation.SC_OK))
      .compose(HttpClientResponse::body)
      .await();

    assertEquals("Hello from origin", body.toString());
    // TLS origin: nested TLS through the CONNECT tunnel (the proxy never sees the encrypted request).
    assertEquals(HttpMethod.CONNECT, proxy.getLastMethod());
  }

  @Test
  public void testHttpsProxy_Http2Origin_tunnelled() throws Exception {
    proxy = new HttpProxy().ssl(Cert.SERVER_JKS.get());
    proxy.start(proxyVertx);
    startHttpsOrigin(HttpVersion.HTTP_2);

    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setSsl(true)
      .setUseAlpn(true)
      .setTrustOptions(Trust.SERVER_JKS.get())
      .setProxyOptions(httpsProxy(proxy.port())));

    String result = client.request(new RequestOptions().setMethod(HttpMethod.GET)
        .setHost(DEFAULT_HTTPS_HOST).setPort(DEFAULT_HTTPS_PORT).setURI("/"))
      .compose(req -> req.send().expecting(HttpResponseExpectation.SC_OK))
      .compose(resp -> resp.body().map(body -> resp.version() + ":" + body))
      .await();

    // h2 rides the CONNECT tunnel for free; ALPN negotiates h2 end-to-end with the origin.
    assertEquals(HttpVersion.HTTP_2 + ":Hello from origin", result);
    assertEquals(HttpMethod.CONNECT, proxy.getLastMethod());
  }

  @Test
  public void testHttpsProxy_proxyAuthentication() throws Exception {
    proxy = new HttpProxy().ssl(Cert.SERVER_JKS.get()).username("user1");
    proxy.start(proxyVertx);
    startHttpOrigin();

    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
      .setProxyOptions(httpsProxy(proxy.port()).setUsername("user1").setPassword("user1")));

    Buffer body = client.request(new RequestOptions().setMethod(HttpMethod.GET)
        .setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT).setURI("/"))
      .compose(req -> req.send().expecting(HttpResponseExpectation.SC_OK))
      .compose(HttpClientResponse::body)
      .await();

    // SC_OK proves the Basic Proxy-Authorization (sent inside the established leg-1 TLS) was accepted;
    // the proxy returns 407 on missing/incorrect credentials.
    assertEquals("Hello from origin", body.toString());
  }

  @Test
  public void testHttpsProxy_mutualTls() throws Exception {
    proxy = new HttpProxy()
      .ssl(Cert.SERVER_JKS.get())
      .requireClientAuth(Trust.CLIENT_JKS.get());
    proxy.start(proxyVertx);
    startHttpOrigin();

    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setProxyOptions(httpsProxy(proxy.port())
      .setSslOptions(new ClientSSLOptions()
        .setTrustOptions(Trust.SERVER_JKS.get())
        .setKeyCertOptions(Cert.CLIENT_JKS.get()))));

    Buffer body = client.request(new RequestOptions().setMethod(HttpMethod.GET)
        .setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT).setURI("/"))
      .compose(req -> req.send().expecting(HttpResponseExpectation.SC_OK))
      .compose(HttpClientResponse::body)
      .await();

    assertEquals("Hello from origin", body.toString());
  }

  // --- Security / failure cases ---------------------------------------------

  @Test
  public void testHttpsProxy_untrustedProxyCertRejected() throws Exception {
    proxy = new HttpProxy().ssl(Cert.SERVER_JKS.get());
    proxy.start(proxyVertx);
    startHttpOrigin();

    client.close();
    // No sslOptions => default trust source (no self-signed cert): the proxy cert is not trusted and
    // there is no plaintext fallback for an HTTPS proxy.
    client = vertx.createHttpClient(new HttpClientOptions()
      .setProxyOptions(new ProxyOptions().setType(ProxyType.HTTPS).setHost("localhost").setPort(proxy.port())));

    // connect must fail rather than hanging or silently downgrading
    assertThatThrownBy(() -> client.request(new RequestOptions().setMethod(HttpMethod.GET)
        .setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT).setURI("/"))
      .compose(req -> req.send())
      .await())
      .isInstanceOf(SSLHandshakeException.class)
      .hasStackTraceContaining("unable to find valid certification path");
  }

  @Test
  public void testHttpsProxy_hostnameMismatchRejected() throws Exception {
    proxy = new HttpProxy().ssl(Cert.SERVER_JKS.get());
    proxy.start(proxyVertx);
    startHttpOrigin();

    client.close();
    // Proxy cert is CN=localhost; connecting via 127.0.0.1 must fail hostname verification (on by default).
    client = vertx.createHttpClient(new HttpClientOptions()
      .setProxyOptions(new ProxyOptions().setType(ProxyType.HTTPS).setHost("127.0.0.1").setPort(proxy.port())
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
  public void testHttpsProxy_againstPlaintextProxyFailsCleanly() throws Exception {
    proxy = new HttpProxy(); // plaintext proxy
    proxy.start(proxyVertx);
    startHttpOrigin();

    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setProxyOptions(httpsProxy(proxy.port())));

    // no silent downgrade: a HTTPS proxy type never falls back to plaintext
    assertThatThrownBy(() -> client.request(new RequestOptions().setMethod(HttpMethod.GET)
        .setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT).setURI("/"))
      .compose(req -> req.send())
      .await())
      .isInstanceOf(SSLHandshakeException.class)
      .hasStackTraceContaining("not an SSL/TLS record");
  }
}
