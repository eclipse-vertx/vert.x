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
import io.vertx.core.http.impl.Origin;
import io.vertx.core.http.Http1ClientConfig;
import io.vertx.core.http.Http2ClientConfig;
import io.vertx.core.http.Http3ClientConfig;
import io.vertx.core.http.HttpClientConfig;
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.core.internal.net.endpoint.EndpointResolverInternal;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.net.endpoint.Endpoint;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.proxy.Proxy;
import io.vertx.test.tls.Cert;
import org.junit.Rule;
import org.junit.Test;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.security.cert.CertificateException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.vertx.core.http.impl.HttpUtils.writeAltSvc;

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
    Set<HttpVersion> tcpVersions = Stream.of(versions).filter(v -> v != HttpVersion.HTTP_3).collect(Collectors.toSet());
    List<HttpVersion> quicVersions = Stream.of(versions).filter(v -> v == HttpVersion.HTTP_3).collect(Collectors.toList());
    if (!tcpVersions.isEmpty()) {
      HttpServer server = vertx.createHttpServer(new HttpServerConfig()
        .setSsl(true)
        .setVersions(tcpVersions),
        new ServerSSLOptions()
          .setSni(true)
          .setKeyCertOptions(cert.get())
      );
      server.requestHandler(request -> {
        Handler<HttpServerRequest> h = handler.get();
        if (h != null) {
          h.handle(request);
        }
      });
      server.listen(port).await();
    }
    if (!quicVersions.isEmpty()) {
      HttpServerConfig config = new HttpServerConfig();
      config.addVersion(HttpVersion.HTTP_3);
      HttpServer server = vertx.createHttpServer(config, new ServerSSLOptions().setKeyCertOptions(cert.get()));
      server.requestHandler(request -> {
        Handler<HttpServerRequest> h = handler.get();
        if (h != null) {
          h.handle(request);
        }
      });
      server.listen(port).await();
    }
    return handler::set;
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    client = null;
  }

  @Test
  public void testHttp1ToHttp2Protocol() {
    testFollowProtocol(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2, "h2=\"localhost:4044\"", "localhost:4044");
  }

  @Test
  public void testHttp1ToHttp2ProtocolSameHost() {
    testFollowProtocol(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2, "h2=\":4044\"", "host2.com:4044");
  }

  @Test
  public void testHttp1ToHttp3Protocol() {
    testFollowProtocol(HttpVersion.HTTP_1_1, HttpVersion.HTTP_3, "h3=\"host2.com:4044\"", "host2.com:4044");
  }

  @Test
  public void testHttp1ToHttp3ProtocolSameHost() {
    testFollowProtocol(HttpVersion.HTTP_1_1, HttpVersion.HTTP_3, "h3=\":4044\"", "host2.com:4044");
  }

  @Test
  public void testHttp2ToHttp3Protocol() {
    testFollowProtocol(HttpVersion.HTTP_2, HttpVersion.HTTP_3, "h3=\"host2.com:4044\"", "host2.com:4044");
  }

  @Test
  public void testHttp2ToHttp3ProtocolSameHost() {
    testFollowProtocol(HttpVersion.HTTP_2, HttpVersion.HTTP_3, "h3=\":4044\"", "host2.com:4044");
  }

  @Test
  public void testExpiration() throws Exception {
    testFollowProtocol(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2, "h2=\"host2.com:4044\";ma=1", "host2.com:4044");
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
    testFollowProtocol(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2, altSvc::get, "host2.com:4044");
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

  private void testFollowProtocol(HttpVersion initialProtocol, HttpVersion upgradedProtocol, String advertisement, String expectedAltUsed) {
    testFollowProtocol(initialProtocol, upgradedProtocol, () -> advertisement, expectedAltUsed);
  }

  private void testFollowProtocol(HttpVersion initialProtocol, HttpVersion upgradedProtocol, Supplier<String> advertisement, String expectedAltUsed) {
    startServer(4043, Cert.SNI_JKS, initialProtocol)
      .accept(request -> {
        assertNull(request.getHeader(HttpHeaders.ALT_USED));
        assertEquals("host2.com", request.connection().indicatedServerName());
        writeAltSvc(request, advertisement.get())
          .end(request.authority().toString(false));
      });
    startServer(4044, Cert.SNI_JKS, upgradedProtocol)
      .accept(request -> {
        assertEquals(expectedAltUsed, request.getHeader(HttpHeaders.ALT_USED));
        assertEquals("host2.com", request.connection().indicatedServerName());
        request
          .response()
          .end(request.authority().toString(false));
      });
    client = vertx.createHttpClient((
        new HttpClientConfig()
          .setFollowAlternativeServices(true)
          .setSsl(true)
          .setVersions(List.of(initialProtocol, upgradedProtocol))),
      new ClientSSLOptions().setTrustAll(true));
    Buffer body = client.request(HttpMethod.GET, 4043, "host2.com", "/")
      .compose(request -> request
        .send()
        .expecting(response -> request.version() == initialProtocol)
        .compose(HttpClientResponse::body)
      ).await();
    assertEquals("host2.com:4043", body.toString());
    body = client.request(new RequestOptions().setHost("host2.com").setProtocolVersion(upgradedProtocol).setPort(4043).setURI("/"))
      .compose(request -> request
        .send()
        .expecting(response -> request.version() == upgradedProtocol)
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
    client = vertx.createHttpClient(new HttpClientOptions().setFollowAlternativeServices(true).setSsl(true).setTrustAll(true));
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
    client = vertx.createHttpClient(new HttpClientOptions().setFollowAlternativeServices(true).setSsl(true).setTrustAll(true));
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
    client = vertx.createHttpClient(new HttpClientOptions().setFollowAlternativeServices(true).setSsl(true).setTrustAll(true));
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
    client = vertx.createHttpClient(new HttpClientOptions().setFollowAlternativeServices(false).setSsl(true).setTrustAll(true));
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
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2).setFollowAlternativeServices(true));
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

  @Test
  public void testAlternativeCaching1() throws Exception {
    // Test that we maintain the information although the server will an advertisement on each request
    int times = testAlternativeCaching("h2=\"host2.com:4044\"", 5, 0L);
    assertEquals(1, times);
  }

  @Test
  public void testAlternativeCaching2() throws Exception {
    // Test that we refresh information when expiration time is short and the server provides identical advertisements
    int times = testAlternativeCaching("h2=\"host2.com:4044\";ma=1", 5, 1000L);
    assertEquals(5, times);
  }

  private int testAlternativeCaching(String altSvc, int num, long sleepTime) throws Exception {
    startServer(4043, Cert.SNI_JKS, HttpVersion.HTTP_1_1)
      .accept(request -> {
        assertNull(request.getHeader(HttpHeaders.ALT_USED));
        assertEquals("host2.com", request.connection().indicatedServerName());
        request
          .response()
          .putHeader(HttpHeaders.ALT_SVC, altSvc)
          .end(request.authority().toString(false));
      });
    client = vertx.createHttpClient(new HttpClientOptions().setFollowAlternativeServices(true).setSsl(true).setTrustAll(true));
    Buffer body = client.request(HttpMethod.GET, 4043, "host2.com", "/")
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::body)
      ).await();
    EndpointResolverInternal resolver = ((HttpClientInternal) client).originResolver();
    Map<Endpoint, Endpoint> map = new IdentityHashMap<>();
    for (int i = 0;i < num;i++) {
      body = client.request(HttpMethod.GET, 4043, "host2.com", "/")
        .compose(request -> request
          .send()
          .compose(HttpClientResponse::body)
        ).await();
      Endpoint endpoint = resolver.resolveEndpoint(new Origin("https", "host2.com", 4043)).await();
      map.put(endpoint, endpoint);
      if (sleepTime > 0) {
        Thread.sleep(sleepTime);
      }
    }
    return map.size();
  }
}
