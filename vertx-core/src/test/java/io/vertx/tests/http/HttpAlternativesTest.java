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

import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.proxy.Proxy;
import io.vertx.test.proxy.ProxyKind;
import io.vertx.test.proxy.WithProxy;
import io.vertx.test.tls.Cert;
import org.junit.Rule;
import org.junit.Test;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class HttpAlternativesTest extends VertxTestBase {

  @Rule
  public Proxy proxy = new Proxy();

  private HttpServer http1xServer;
  private HttpServer http2Server;
  private HttpClient client;

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.getAddressResolverOptions().setHostsValue(Buffer.buffer("" +
      "127.0.0.1 localhost\n" +
      "127.0.0.1 host2.com\n" +
      "127.0.0.1 http1x\n" +
      "127.0.0.1 http2\n"
    ));
    return options;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void testPlainText() {
    testPlainText(() -> "h2c=\"http2:8081\"", "http2:8081", HttpVersion.HTTP_2);
  }

  @Test
  public void testPlainTextSameHost() {
    testPlainText(() -> "h2c=\":8081\"", "http1x:8081", HttpVersion.HTTP_2);
  }

  @Test
  public void testPlainTextExpiration() throws Exception {
    testPlainText(() -> "h2c=\"http2:8081\";ma=1", "http2:8081", HttpVersion.HTTP_2);
    Thread.sleep(1500);
    Buffer body = client.request(new RequestOptions().setHost("http1x").setProtocolVersion(HttpVersion.HTTP_2).setPort(8080).setURI("/"))
      .compose(request -> request
        .send()
        .expecting(response -> request.version() == HttpVersion.HTTP_1_1)
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("http1x:8080", body.toString());
  }

  @Test
  public void testPlainTextOverwrite() {
    testPlainTextInvalidate("h2c=\"http2:8082\"");
  }

  @Test
  public void testPlainTextClear() {
    testPlainTextInvalidate("clear");
  }

  private void testPlainTextInvalidate(String altSvcInvalidator) {
    AtomicReference<String> altSvc = new AtomicReference<>("h2c=\"http2:8081\"");
    testPlainText(altSvc::get, "http2:8081", HttpVersion.HTTP_2);
    altSvc.set(altSvcInvalidator);
    Buffer body = client.request(new RequestOptions().setHost("http1x").setProtocolVersion(HttpVersion.HTTP_1_1).setPort(8080).setURI("/"))
      .compose(request -> request
        .send()
        .expecting(response -> request.version() == HttpVersion.HTTP_1_1)
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("http1x:8080", body.toString());
    try {
      client.request(new RequestOptions().setHost("http1x").setProtocolVersion(HttpVersion.HTTP_2).setPort(8080).setURI("/")).await();
    } catch (Exception e) {
      assertTrue(e instanceof ConnectException);
    }
  }

  @Test
  public void testPlainTextIgnoreH2() {
    testPlainText(() -> "h2=\"http2:8081\"", null, HttpVersion.HTTP_1_1);
  }

  private void testPlainText(Supplier<String> altSvcValue, String expectedAltUsed, HttpVersion expectedProtocol) {
    http1xServer = vertx.createHttpServer(new HttpServerOptions().setHttp2ClearTextEnabled(false));
    http1xServer.requestHandler(request -> {
      assertNull(request.getHeader(HttpHeaders.ALT_USED));
      request
        .response()
        .putHeader(HttpHeaders.ALT_SVC, altSvcValue.get())
        .end(request.authority().toString(false));
    });
    http1xServer.listen(8080).await();
    http2Server = vertx.createHttpServer(new HttpServerOptions().setHttp2ClearTextEnabled(true));
    http2Server.requestHandler(request -> {
       assertEquals(expectedAltUsed, request.getHeader(HttpHeaders.ALT_USED));
      request
        .response()
        .end(request.authority().toString(false));
    });
    http2Server.listen(8081).await();
    testUseAlternative(expectedProtocol);
  }

  private void testUseAlternative(HttpVersion expectedVersion) {
    client = vertx.createHttpClient(new HttpClientOptions().setFollowAlternativeServices(true));
    Buffer body = client.request(HttpMethod.GET, 8080, "http1x", "/")
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("http1x:8080", body.toString());
    body = client.request(new RequestOptions().setHost("http1x").setProtocolVersion(HttpVersion.HTTP_2).setPort(8080).setURI("/"))
      .compose(request -> request
        .send()
        .expecting(response -> request.version() == expectedVersion)
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("http1x:8080", body.toString());
  }

  @Test
  public void testEvictInvalidAlternative() {
    http1xServer = vertx.createHttpServer(new HttpServerOptions().setHttp2ClearTextEnabled(false));
    http1xServer.requestHandler(request -> {
      assertNull(request.getHeader(HttpHeaders.ALT_USED));
      request
        .response()
        .putHeader(HttpHeaders.ALT_SVC, "h2c=\"http2:8081\";ma=1")
        .end(request.authority().toString(false));
    });
    http1xServer.listen(8080).await();
    client = vertx.createHttpClient(new HttpClientOptions().setFollowAlternativeServices(true));
    Buffer body = client.request(HttpMethod.GET, 8080, "http1x", "/")
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("http1x:8080", body.toString());
    try {
      client.request(new RequestOptions().setHost("http1x").setProtocolVersion(HttpVersion.HTTP_2).setPort(8080).setURI("/"))
        .compose(request -> request
          .send()
          .expecting(response -> request.version() == HttpVersion.HTTP_2)
          .compose(HttpClientResponse::body)
        ).await();
      fail();
    } catch (Exception e) {
      assertEquals(ConnectException.class, e.getClass().getSuperclass());
    }
    client.request(new RequestOptions().setHost("http1x").setProtocolVersion(HttpVersion.HTTP_2).setPort(8080).setURI("/"))
      .compose(request -> request
        .send()
        .expecting(response -> request.version() == HttpVersion.HTTP_1_1)
        .compose(HttpClientResponse::body)
      ).await();
  }

  @Test
  public void testTLSSelectProtocol() {
    testTLSSelectProtocol("http2", "http2:4044");
  }

  @Test
  public void testTLSSelectProtocolSameHost() {
    testTLSSelectProtocol("", "host2.com:4044");
  }

  private void testTLSSelectProtocol(String host, String expectedAltUsed) {
    http1xServer = vertx.createHttpServer(new HttpServerOptions()
      .setSsl(true)
      .setUseAlpn(true)
      .setSni(true)
      .setAlpnVersions(List.of(HttpVersion.HTTP_1_1))
      .setKeyCertOptions(Cert.SNI_JKS.get()));
    http1xServer.requestHandler(request -> {
      assertNull(request.getHeader(HttpHeaders.ALT_USED));
      assertEquals("host2.com", request.connection().indicatedServerName());
      request
        .response()
        .putHeader(HttpHeaders.ALT_SVC, "h2=\"" + host + ":4044\"")
        .end(request.authority().toString(false));
    });
    http1xServer.listen(4043).await();
    http2Server = vertx.createHttpServer(new HttpServerOptions()
      .setSsl(true)
      .setSsl(true)
      .setUseAlpn(true)
      .setSni(true)
      .setAlpnVersions(List.of(HttpVersion.HTTP_2))
      .setKeyCertOptions(Cert.SNI_JKS.get()));
    http2Server.requestHandler(request -> {
      assertEquals(expectedAltUsed, request.getHeader(HttpHeaders.ALT_USED));
      assertEquals("host2.com", request.connection().indicatedServerName());
      request
        .response()
        .end(request.authority().toString(false));
    });
    http2Server.listen(4044).await();
    client = vertx.createHttpClient(new HttpClientOptions().setFollowAlternativeServices(true).setSsl(true).setTrustAll(true).setUseAlpn(true));
    Buffer body = client.request(HttpMethod.GET, 4043, "host2.com", "/")
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("host2.com:4043", body.toString());
    body = client.request(new RequestOptions().setHost("host2.com").setProtocolVersion(HttpVersion.HTTP_2).setPort(4043).setURI("/"))
      .compose(request -> request
        .send()
        .expecting(response -> request.version() == HttpVersion.HTTP_2)
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("host2.com:4043", body.toString());
  }

  @Test
  public void testTLSIgnoreAlternativeWithoutSNI() {
    http1xServer = vertx.createHttpServer(new HttpServerOptions()
      .setSsl(true)
      .setUseAlpn(true)
      .setSni(true)
      .setAlpnVersions(List.of(HttpVersion.HTTP_1_1))
      .setKeyCertOptions(Cert.SERVER_JKS.get()));
    http1xServer.requestHandler(request -> {
      assertEquals(null, request.connection().indicatedServerName());
      request
        .response()
        .putHeader(HttpHeaders.ALT_SVC, "h2=\":4044\"")
        .end(request.authority().toString(false));
    });
    http1xServer.listen(4043).await();
    client = vertx.createHttpClient(new HttpClientOptions().setFollowAlternativeServices(true).setSsl(true).setTrustAll(true).setUseAlpn(true));
    Buffer body = client.request(HttpMethod.GET, 4043, "localhost", "/")
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("localhost:4043", body.toString());
    body = client.request(new RequestOptions().setHost("localhost").setProtocolVersion(HttpVersion.HTTP_2).setPort(4043).setURI("/"))
      .compose(request -> request
        .send()
        .expecting(response -> request.version() == HttpVersion.HTTP_1_1)
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("localhost:4043", body.toString());
  }

  @Test
  public void testCertificateValidation() {
    http1xServer = vertx.createHttpServer(new HttpServerOptions()
      .setSsl(true)
      .setUseAlpn(true)
      .setSni(true)
      .setAlpnVersions(List.of(HttpVersion.HTTP_1_1))
      .setKeyCertOptions(Cert.SNI_JKS.get()));
    http1xServer.requestHandler(request -> {
      assertNull(request.getHeader(HttpHeaders.ALT_USED));
      assertEquals("host2.com", request.connection().indicatedServerName());
      request
        .response()
        .putHeader(HttpHeaders.ALT_SVC, "h2=\":4044\"")
        .end(request.authority().toString(false));
    });
    http1xServer.listen(4043).await();
    http2Server = vertx.createHttpServer(new HttpServerOptions()
      .setSsl(true)
      .setSsl(true)
      .setUseAlpn(true)
      .setAlpnVersions(List.of(HttpVersion.HTTP_2))
      .setKeyCertOptions(Cert.SERVER_JKS.get()));
    http2Server.requestHandler(request -> {
      fail();
    });
    http2Server.listen(4044).await();
    client = vertx.createHttpClient(new HttpClientOptions().setFollowAlternativeServices(true).setSsl(true).setTrustAll(true).setUseAlpn(true));
    Buffer body = client.request(HttpMethod.GET, 4043, "host2.com", "/")
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("host2.com:4043", body.toString());
    try {
      client.request(new RequestOptions().setHost("host2.com").setProtocolVersion(HttpVersion.HTTP_2).setPort(4043).setURI("/"))
        .compose(request -> request
          .send()
          .expecting(response -> request.version() == HttpVersion.HTTP_2)
          .compose(HttpClientResponse::body)
        ).await();
      fail();
    } catch (Exception e) {
      assertEquals(SSLHandshakeException.class, e.getClass());
      Throwable root = e;
      while (root.getCause() != null) {
        root = root.getCause();
      }
      assertEquals(CertificateException.class, root.getClass());
      assertEquals("No name matching host2.com found", root.getMessage());
    }
  }

  @Test
  public void testIgnoreAlternativeServicesAdvertisements() {
    http1xServer = vertx.createHttpServer(new HttpServerOptions().setHttp2ClearTextEnabled(false));
    http1xServer.requestHandler(request -> {
      assertNull(request.getHeader(HttpHeaders.ALT_USED));
      request
        .response()
        .putHeader(HttpHeaders.ALT_SVC, "h2c=\"http2:8081\";ma=1")
        .end(request.authority().toString(false));
    });
    http1xServer.listen(8080).await();
    client = vertx.createHttpClient(new HttpClientOptions().setFollowAlternativeServices(false));
    Buffer body = client.request(HttpMethod.GET, 8080, "http1x", "/")
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("http1x:8080", body.toString());
    body = client.request(new RequestOptions().setHost("http1x").setProtocolVersion(HttpVersion.HTTP_2).setPort(8080).setURI("/"))
      .compose(request -> request
        .send()
        .expecting(response -> request.version() == HttpVersion.HTTP_1_1)
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("http1x:8080", body.toString());
  }

//  @WithProxy(kind = ProxyKind.SOCKS5, localhosts = { "localhost", "h2c.socks5.proxy" } )
//  @Test
//  public void testSocks5Proxy() {
//    http1xServer = vertx.createHttpServer(new HttpServerOptions().setHttp2ClearTextEnabled(false));
//    http1xServer.requestHandler(request -> {
//      assertNull(request.getHeader(HttpHeaders.ALT_USED));
//      request
//        .response()
//        .putHeader(HttpHeaders.ALT_SVC, "h2c=\"h2c.socks5.proxy:8081\"")
//        .end(request.authority().toString(false));
//    });
//    http1xServer.listen(8080).await();
//    http2Server = vertx.createHttpServer(new HttpServerOptions().setHttp2ClearTextEnabled(true));
//    http2Server.requestHandler(request -> {
//      assertEquals("h2c.socks5.proxy:8081", request.getHeader(HttpHeaders.ALT_USED));
//      request
//        .response()
//        .end(request.authority().toString(false));
//    });
//    http2Server.listen(8081).await();
//    client = vertx.createHttpClient(new HttpClientOptions().setFollowAlternativeServices(true));
//    Buffer body = client.request(HttpMethod.GET, 8080, "http1x", "/")
//      .compose(request -> request
//        .send()
//        .compose(HttpClientResponse::body)
//      ).await();
//    assertEquals("http1x:8080", body.toString());
//    body = client.request(new RequestOptions().setHost("http1x").setProxyOptions(proxy.options()).setProtocolVersion(HttpVersion.HTTP_2).setPort(8080).setURI("/"))
//      .compose(request -> request
//        .send()
//        .expecting(response -> request.version() == HttpVersion.HTTP_2)
//        .compose(HttpClientResponse::body)
//      ).await();
//    assertEquals("http1x:8080", body.toString());
//  }

  @Test
  public void testLoadBalancing() {

  }
}
