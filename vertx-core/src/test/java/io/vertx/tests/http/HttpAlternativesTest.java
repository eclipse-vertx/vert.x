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

import io.vertx.core.Handler;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.proxy.Proxy;
import io.vertx.test.tls.Cert;
import org.junit.Rule;
import org.junit.Test;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class HttpAlternativesTest extends VertxTestBase {

  @Rule
  public Proxy proxy = new Proxy();

  private HttpClient client;

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.getAddressResolverOptions().setHostsValue(Buffer.buffer("" +
      "127.0.0.1 localhost\n" +
      "127.0.0.1 host2.com\n"
    ));
    return options;
  }

  private Consumer<Handler<HttpServerRequest>> startServer(int port, Cert<? extends KeyCertOptions> cert, HttpVersion... versions) {
    AtomicReference<Handler<HttpServerRequest>> handler = new AtomicReference<>();
    HttpServer server = vertx.createHttpServer(new HttpServerOptions()
      .setSsl(true)
      .setUseAlpn(true)
      .setSni(true)
      .setAlpnVersions(List.of(versions))
      .setKeyCertOptions(cert.get()));
    server.requestHandler(request -> {
      Handler<HttpServerRequest> h = handler.get();
      if (h != null) {
        h.handle(request);
      }
    });
    server.listen(port).await();
    return handler::set;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
//    http1xServer = vertx.createHttpServer(new HttpServerOptions()
//      .setSsl(true)
//      .setUseAlpn(true)
//      .setSni(true)
//      .setAlpnVersions(List.of(HttpVersion.HTTP_1_1))
//      .setKeyCertOptions(Cert.SNI_JKS.get()));
//    http1xServer.requestHandler(request -> {
//      Handler<HttpServerRequest> handler = http1xHandler;
//      if (handler != null) {
//        handler.handle(request);
//      }
//    });
//    http1xServer.listen(4043).await();
//    http2Server = vertx.createHttpServer(new HttpServerOptions()
//      .setSsl(true)
//      .setSsl(true)
//      .setUseAlpn(true)
//      .setSni(true)
//      .setAlpnVersions(List.of(HttpVersion.HTTP_2))
//      .setKeyCertOptions(Cert.SNI_JKS.get()));
//    http2Server.requestHandler(request -> {
//      Handler<HttpServerRequest> handler = http2Handler;
//      if (handler != null) {
//        handler.handle(request);
//      }
//    });
//    http2Server.listen(4044).await();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    client = null;
  }

  @Test
  public void testFollowH2Protocol() {
    testH2Protocol("h2=\"host2.com:4044\"", "host2.com:4044");
  }

  @Test
  public void testFollowH2ProtocolSameHost() {
    testH2Protocol("h2=\":4044\"", "host2.com:4044");
  }

  @Test
  public void testExpiration() throws Exception {
    testH2Protocol("h2=\"host2.com:4044\";ma=1", "host2.com:4044");
    Thread.sleep(1500);
    Buffer body = client.request(new RequestOptions().setHost("host2.com").setProtocolVersion(HttpVersion.HTTP_2).setPort(4043).setURI("/"))
      .compose(request -> request
        .send()
        .expecting(response -> request.version() == HttpVersion.HTTP_1_1)
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("host2.com:4043", body.toString());
  }

  @Test
  public void testOverwriteInvalidation() {
    testInvalidation("h2=\"host2.com:4045\"");
  }

  @Test
  public void testClearInvalidation() {
    testInvalidation("clear");
  }

  @Test
  public void testInvalidProtocolInvalidation() {
    testInvalidation("h2c=\"host2.com:4044\"");
  }

  private void testInvalidation(String altSvcInvalidator) {
    AtomicReference<String> altSvc = new AtomicReference<>("h2=\"host2.com:4044\"");
    testH2Protocol(altSvc::get, "host2.com:4044");
    altSvc.set(altSvcInvalidator);
    Buffer body = client.request(new RequestOptions().setHost("host2.com").setProtocolVersion(HttpVersion.HTTP_1_1).setPort(4043).setURI("/"))
      .compose(request -> request
        .send()
        .expecting(response -> request.version() == HttpVersion.HTTP_1_1)
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("host2.com:4043", body.toString());
    try {
      client.request(new RequestOptions().setHost("host2.com").setProtocolVersion(HttpVersion.HTTP_2).setPort(4043).setURI("/")).await();
    } catch (Exception e) {
      assertTrue(e instanceof ConnectException);
    }
  }

  private void testH2Protocol(String advertisement, String expectedAltUsed) {
    testH2Protocol(() -> advertisement, expectedAltUsed);
  }

  private void testH2Protocol(Supplier<String> advertisement, String expectedAltUsed) {
    startServer(4043, Cert.SNI_JKS, HttpVersion.HTTP_1_1)
      .accept(request -> {
        assertNull(request.getHeader(HttpHeaders.ALT_USED));
        assertEquals("host2.com", request.connection().indicatedServerName());
        request
          .response()
          .putHeader(HttpHeaders.ALT_SVC, advertisement.get())
          .end(request.authority().toString(false));
      });
    startServer(4044, Cert.SNI_JKS, HttpVersion.HTTP_2)
      .accept(request -> {
        assertEquals(expectedAltUsed, request.getHeader(HttpHeaders.ALT_USED));
        assertEquals("host2.com", request.connection().indicatedServerName());
        request
          .response()
          .end(request.authority().toString(false));
      });
    client = vertx.createHttpClient(new HttpClientOptions().setFollowAlternativeServices(true).setSsl(true).setTrustAll(true).setUseAlpn(true));
    Buffer body = client.request(HttpMethod.GET, 4043, "host2.com", "/")
      .compose(request -> request
        .send()
        .expecting(response -> request.version() == HttpVersion.HTTP_1_1)
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
  public void testEvictInvalidAlternative() {
    startServer(4043, Cert.SNI_JKS, HttpVersion.HTTP_1_1)
      .accept(request -> {
        assertNull(request.getHeader(HttpHeaders.ALT_USED));
        request
          .response()
          .putHeader(HttpHeaders.ALT_SVC, "h2=\"host2.com:4044\"")
          .end(request.authority().toString(false));
      });
    client = vertx.createHttpClient(new HttpClientOptions().setFollowAlternativeServices(true).setSsl(true).setTrustAll(true).setUseAlpn(true));
    Buffer body = client.request(HttpMethod.GET, 4043, "host2.com", "/")
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("host2.com:4043", body.toString());
    try {
      client.request(new RequestOptions().setHost("host2.com").setProtocolVersion(HttpVersion.HTTP_2).setPort(4043).setURI("/"))
        .await();
      fail();
    } catch (Exception e) {
      assertEquals(ConnectException.class, e.getClass().getSuperclass());
    }
    client.request(new RequestOptions().setHost("host2.com").setProtocolVersion(HttpVersion.HTTP_2).setPort(4043).setURI("/"))
      .compose(request -> request
        .send()
        .expecting(response -> request.version() == HttpVersion.HTTP_1_1)
        .compose(HttpClientResponse::body)
      ).await();
  }

  @Test
  public void testIgnoreAlternativeWithoutSNI() {
    startServer(4043, Cert.SNI_JKS, HttpVersion.HTTP_1_1)
      .accept(request -> {
        assertEquals(null, request.connection().indicatedServerName());
        request
          .response()
          .putHeader(HttpHeaders.ALT_SVC, "h2=\":4044\"")
          .end(request.authority().toString(false));
      });
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
    startServer(4043, Cert.SNI_JKS, HttpVersion.HTTP_1_1)
      .accept(request -> {
        assertNull(request.getHeader(HttpHeaders.ALT_USED));
        assertEquals("host2.com", request.connection().indicatedServerName());
        request
          .response()
          .putHeader(HttpHeaders.ALT_SVC, "h2=\":4044\"")
          .end(request.authority().toString(false));
      });
    startServer(4044, Cert.SERVER_JKS, HttpVersion.HTTP_2)
      .accept(request -> {
        fail();
      });
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
    startServer(4043, Cert.SNI_JKS, HttpVersion.HTTP_1_1)
      .accept(request -> {
        assertNull(request.getHeader(HttpHeaders.ALT_USED));
        request
          .response()
          .putHeader(HttpHeaders.ALT_SVC, "h2=\"host2.com:4044\"")
          .end(request.authority().toString(false));
      });
    client = vertx.createHttpClient(new HttpClientOptions().setFollowAlternativeServices(false).setSsl(true).setTrustAll(true).setUseAlpn(true));
    Buffer body = client.request(HttpMethod.GET, 4043, "host2.com", "/")
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("host2.com:4043", body.toString());
    body = client.request(new RequestOptions().setHost("host2.com").setProtocolVersion(HttpVersion.HTTP_2).setPort(4043).setURI("/"))
      .compose(request -> request
        .send()
        .expecting(response -> request.version() == HttpVersion.HTTP_1_1)
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("host2.com:4043", body.toString());
  }

  @Test
  public void testIgnoreAlternativeServicesAdvertisements2() {
    HttpServer server = vertx.createHttpServer(new HttpServerOptions().setHttp2ClearTextEnabled(false));
    server.requestHandler(request -> {
      assertNull(request.getHeader(HttpHeaders.ALT_USED));
      request
        .response()
        .putHeader(HttpHeaders.ALT_SVC, "h2c=\"host2.com:8081\"")
        .end(request.authority().toString(false));
    });
    server.listen(8080).await();
    client = vertx.createHttpClient(new HttpClientOptions().setFollowAlternativeServices(true));
    Buffer body = client.request(HttpMethod.GET, 8080, "host2.com", "/")
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("host2.com:8080", body.toString());
    body = client.request(new RequestOptions().setHost("host2.com").setProtocolVersion(HttpVersion.HTTP_2).setPort(8080).setURI("/"))
      .compose(request -> request
        .send()
        .expecting(response -> request.version() == HttpVersion.HTTP_1_1)
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("host2.com:8080", body.toString());
  }
}
