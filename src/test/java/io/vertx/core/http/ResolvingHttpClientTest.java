package io.vertx.core.http;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.HttpClientInternal;
import io.vertx.core.net.*;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakeresolver.*;
import io.vertx.test.proxy.HttpProxy;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;

public class ResolvingHttpClientTest extends VertxTestBase {

  private BiConsumer<Integer, HttpServerRequest> requestHandler;
  private List<HttpServer> servers;

  private void startServers(int numServers) throws Exception {
    startServers(numServers, new HttpServerOptions());
  }

  private void startServers(int numServers, HttpServerOptions options) throws Exception {
    if (servers != null) {
      throw new IllegalStateException();
    }
    servers = new ArrayList<>();
    for (int i = 0;i < numServers;i++) {
      int val = i;
      HttpServer server = vertx
        .createHttpServer(options)
        .requestHandler(req -> {
        BiConsumer<Integer, HttpServerRequest> handler = requestHandler;
        if (handler != null) {
          handler.accept(val, req);
        } else {
          req.response().setStatusCode(404).end();
        }
      });
      servers.add(server);
      awaitFuture(server.listen(8080 + i, "localhost"));
    }
  }

  @Test
  public void testResolveServers() throws Exception {
    int numServers = 2;
    waitFor(numServers * 2);
    startServers(numServers);
    requestHandler = (idx, req) -> req.response().end("server-" + idx);
    FakeAddressResolver resolver = new FakeAddressResolver();
    resolver.registerAddress("example.com", Arrays.asList(SocketAddress.inetSocketAddress(8080, "localhost"), SocketAddress.inetSocketAddress(8081, "localhost")));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(resolver)
      .build();
    Set<String> responses = Collections.synchronizedSet(new HashSet<>());
    for (int i = 0;i < numServers * 2;i++) {
      client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
        .send()
        .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
        .compose(HttpClientResponse::body)
      ).onComplete(onSuccess(v -> {
        responses.add(v.toString());
        complete();
      }));
    }
    await();
    Assert.assertEquals(new HashSet<>(Arrays.asList("server-0", "server-1")), responses);
  }

  @Test
  public void testResolveConnectFailure() throws Exception {
    requestHandler = (idx, req) -> req.response().end("server-" + idx);
    FakeAddressResolver resolver = new FakeAddressResolver();
    resolver.registerAddress("example.com", Arrays.asList(SocketAddress.inetSocketAddress(8080, "localhost"), SocketAddress.inetSocketAddress(8081, "localhost")));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(resolver)
      .build();
    client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
      .send()
      .compose(HttpClientResponse::body)
    ).transform(ar -> {
      if (ar.failed()) {
        List<FakeEndpoint> addresses = resolver.endpoints("example.com");
        assertEquals(Collections.singletonList(SocketAddress.inetSocketAddress(8081, "localhost")), addresses);
        return client.request(HttpMethod.GET, 8080, "example.com", "/").compose(req -> req
          .send()
          .compose(HttpClientResponse::body));
      } else {
        return Future.succeededFuture();
      }
    }).onComplete(onFailure(err -> {
      assertTrue(resolver.addresses().isEmpty());
      testComplete();
    }));
    await();
  }

  @Test
  public void testShutdownServers() throws Exception {
    int numServers = 4;
    requestHandler = (idx, req) -> req.response().end("server-" + idx);
    startServers(numServers);
    FakeAddressResolver resolver = new FakeAddressResolver();
    resolver.registerAddress("example.com", Arrays.asList(
      SocketAddress.inetSocketAddress(8080, "localhost"),
      SocketAddress.inetSocketAddress(8081, "localhost"),
      SocketAddress.inetSocketAddress(8082, "localhost"),
      SocketAddress.inetSocketAddress(8083, "localhost")
    ));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(resolver)
      .build();
    CountDownLatch latch = new CountDownLatch(numServers);
    for (int i = 0;i < numServers;i++) {
      client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
        .send()
        .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
        .compose(HttpClientResponse::body)
      ).onComplete(onSuccess(v -> {
        latch.countDown();
      }));
    }
    awaitLatch(latch);
    List<FakeEndpoint> endpoints = resolver.endpoints("example.com");
    for (int i = 0;i < servers.size();i++) {
      int expected = endpoints.size() - 1;
      awaitFuture(servers.get(i).close());
      assertWaitUntil(() -> endpoints.size() == expected);
    }
    assertWaitUntil(resolver.addresses()::isEmpty);
  }

  @Test
  public void testResolveToSameSocketAddress() throws Exception {
    requestHandler = (idx, req) -> req.response().end("server-" + idx);
    startServers(1);
    FakeAddressResolver resolver = new FakeAddressResolver();
    SocketAddress address = SocketAddress.inetSocketAddress(8080, "localhost");
    resolver.registerAddress("server1.com", List.of(address));
    resolver.registerAddress("server2.com", List.of(address));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(resolver)
      .build();
    Future<Buffer> result = client.request(new RequestOptions().setServer(new FakeAddress("server1.com"))).compose(req -> req
      .send()
      .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
      .compose(HttpClientResponse::body)
    ).compose(body -> client
      .request(new RequestOptions().setServer(new FakeAddress("server2.com")))
      .compose(req -> req
        .send()
        .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
        .compose(HttpClientResponse::body)
      ));
    awaitFuture(result);
    awaitFuture(servers.get(0).close());
    assertWaitUntil(() -> resolver.addresses().isEmpty());
  }

  @Test
  public void testResolveToSameSocketAddressWithProxy() throws Exception {
    requestHandler = (idx, req) -> req.response().end("server-" + idx);
    startServers(1);

    HttpProxy proxy = new HttpProxy();
    proxy.start(vertx);

    FakeAddressResolver resolver = new FakeAddressResolver();
    resolver.registerAddress("example.com", Arrays.asList(SocketAddress.inetSocketAddress(8080, "localhost")));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(resolver)
      .build();
    RequestOptions request1 = new RequestOptions().setServer(new FakeAddress("example.com"));
    RequestOptions request2 = new RequestOptions(request1).setProxyOptions(new ProxyOptions()
        .setHost("localhost")
        .setPort(proxy.defaultPort())
      .setType(ProxyType.HTTP));
    List<RequestOptions> requests = Arrays.asList(request1, request2);
    List<Buffer> responses = new ArrayList<>();
    for (RequestOptions request : requests) {
      Future<Buffer> result = client.request(request).compose(req -> req
        .send()
        .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
        .compose(HttpClientResponse::body)
      );
      Buffer response = awaitFuture(result);
      responses.add(response);
    }
    assertNotNull(proxy.lastLocalAddress());
  }

  @Test
  public void testResolveFailure() {
    Exception cause = new Exception("Not found");
    FakeAddressResolver lookup = new FakeAddressResolver() {
      @Override
      public Future<FakeState> resolve(FakeAddress address) {
        return Future.failedFuture(cause);
      }
    };
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(lookup)
      .build();
    client.request(new RequestOptions().setServer(new FakeAddress("foo.com"))).compose(req -> req
      .send()
      .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
      .compose(HttpClientResponse::body)
    ).onComplete(onFailure(err -> {
      assertSame(cause, err);
      testComplete();
    }));
    await();
  }

  @Test
  public void testUseInvalidAddress() {
    FakeAddressResolver lookup = new FakeAddressResolver();
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(lookup)
      .build();
    client.request(new RequestOptions().setServer(new Address() {
    })).compose(req -> req
      .send()
      .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
      .compose(HttpClientResponse::body)
    ).onComplete(onFailure(err -> {
      assertTrue(err.getMessage().contains("Cannot resolve address"));
      testComplete();
    }));
    await();
  }

  @Test
  public void testKeepAliveTimeout() throws Exception {
    startServers(1);
    requestHandler = (idx, req) -> req.response().end("server-" + idx);
    CountDownLatch closedLatch = new CountDownLatch(1);
    FakeAddressResolver resolver = new FakeAddressResolver();
    resolver.registerAddress("example.com", Arrays.asList(SocketAddress.inetSocketAddress(8080, "localhost")));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .with(new HttpClientOptions().setKeepAliveTimeout(1))
      .withConnectHandler(conn -> {
        conn.closeHandler(v -> {
          closedLatch.countDown();
        });
      })
      .withAddressResolver(resolver)
      .build();
    client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
      .send()
      .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
      .compose(HttpClientResponse::body)
    ).onComplete(onSuccess(v -> {
    }));
    awaitLatch(closedLatch);
    assertWaitUntil(() -> resolver.addresses().isEmpty());
  }

  @Test
  public void testStatistics() throws Exception {
    startServers(1);
    requestHandler = (idx, req) -> {
      vertx.setTimer(500, id -> {
        req.response().end();
      });
    };
    FakeAddressResolver resolver = new FakeAddressResolver();
    resolver.registerAddress("example.com", Arrays.asList(SocketAddress.inetSocketAddress(8080, "localhost")));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .with(new HttpClientOptions().setKeepAliveTimeout(1))
      .withAddressResolver(resolver)
      .build();
    awaitFuture(client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
      .send()
      .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
      .compose(HttpClientResponse::body)
    ));
    FakeEndpoint endpoint = resolver.endpoints("example.com").get(0);
    assertWaitUntil(() -> endpoint.metrics().size() == 1);
    FakeMetric metric = endpoint.metrics().get(0);
    assertTrue(metric.requestEnd() - metric.requestBegin() >= 0);
    assertTrue(metric.responseBegin() - metric.requestEnd() > 500);
    assertTrue(metric.responseEnd() - metric.responseBegin() >= 0);
  }

  @Test
  public void testInvalidation() throws Exception {
    startServers(2);
    requestHandler = (idx, req) -> {
      req.response().end("" + idx);
    };
    FakeAddressResolver resolver = new FakeAddressResolver();
    SocketAddress addr1 = SocketAddress.inetSocketAddress(8080, "localhost");
    SocketAddress addr2 = SocketAddress.inetSocketAddress(8081, "localhost");
    resolver.registerAddress("example.com", Arrays.asList(addr1));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(resolver)
      .build();
    String res = awaitFuture(client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
      .send()
      .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
      .compose(HttpClientResponse::body)
    )).toString();
    assertEquals("0", res);
    resolver.registerAddress("example.com", List.of(addr2));
    res = awaitFuture(client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
      .send()
      .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
      .compose(HttpClientResponse::body)
    )).toString();
    assertEquals("1", res);
  }

  @Test
  public void testSSL() throws Exception {
    testSSL(new RequestOptions()
      .setMethod(HttpMethod.GET)
      .setServer(new FakeAddress("example.com"))
      .setURI("/"), false, true);
  }

  @Test
  public void testSSLOverridePeer() throws Exception {
    testSSL(new RequestOptions()
      .setMethod(HttpMethod.GET)
      .setServer(new FakeAddress("example.com"))
      .setHost("example.com")
      .setPort(8080)
      .setURI("/"), true, true);
  }

  @Test
  public void testSSLOverridePeerNoVerify() throws Exception {
    testSSL(new RequestOptions()
      .setMethod(HttpMethod.GET)
      .setServer(new FakeAddress("example.com"))
      .setHost("example.com")
      .setPort(8080)
      .setURI("/"), false, false);
  }

  private void testSSL(RequestOptions request, boolean expectFailure, boolean verifyHost) throws Exception {
    startServers(1, new HttpServerOptions()
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS.get()));
    requestHandler = (idx, req) -> {
      req.response().end("" + idx);
    };
    FakeAddressResolver resolver = new FakeAddressResolver();
    SocketAddress addr1 = SocketAddress.inetSocketAddress(8080, "localhost");
    resolver.registerAddress("example.com", Arrays.asList(addr1));
    HttpClient client = vertx.httpClientBuilder()
      .with(new HttpClientOptions()
        .setSsl(true)
        .setTrustOptions(Trust.SERVER_JKS.get())
        .setVerifyHost(verifyHost)
      )
      .withAddressResolver(resolver)
      .build();
    try {
      String res = awaitFuture(client.request(request).compose(req -> req
        .send()
        .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
        .compose(HttpClientResponse::body)
      )).toString();
      assertFalse(expectFailure);
      assertEquals("0", res);
    } catch (Exception e) {
      assertTrue(expectFailure);
    }
  }
}
