package io.vertx.core.http;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.HttpClientInternal;
import io.vertx.core.net.*;
import io.vertx.core.spi.resolver.address.Endpoint;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakeloadbalancer.FakeLoadBalancer;
import io.vertx.test.fakeresolver.*;
import io.vertx.test.proxy.HttpProxy;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

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

  @Ignore
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
      public Future<FakeState> resolve(Function<FakeEndpoint, Endpoint<FakeEndpoint>> factory, FakeAddress address) {
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
    FakeLoadBalancer lb = new FakeLoadBalancer();
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .with(new HttpClientOptions().setKeepAliveTimeout(1))
      .withAddressResolver(resolver)
      .withLoadBalancer(lb)
      .build();
    awaitFuture(client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
      .send()
      .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
      .compose(HttpClientResponse::body)
    ));
    FakeLoadBalancer.FakeEndpointMetrics endpoint = lb.endpoints().get(0);
    FakeLoadBalancer.FakeMetric metric = endpoint.metrics().get(0);
    assertTrue(metric.requestEnd() - metric.requestBegin() >= 0);
    assertTrue(metric.responseBegin() - metric.requestEnd() > 500);
    assertTrue(metric.responseEnd() - metric.responseBegin() >= 0);
  }

  @Test
  public void testStatisticsReportingFailure0() throws Exception {
    startServers(1);
    AtomicInteger count = new AtomicInteger();
    requestHandler = (idx, req) -> {
      count.incrementAndGet();
    };
    FakeAddressResolver resolver = new FakeAddressResolver();
    FakeLoadBalancer lb = new FakeLoadBalancer();
    resolver.registerAddress("example.com", Arrays.asList(SocketAddress.inetSocketAddress(8080, "localhost")));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .with(new HttpClientOptions().setKeepAliveTimeout(1))
      .withAddressResolver(resolver)
      .withLoadBalancer(lb)
      .build();
    List<Future<Buffer>> futures = new ArrayList<>();
    for (int i = 0;i < 5;i++) {
      Future<Buffer> fut = client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
        .send()
        .compose(HttpClientResponse::body));
      futures.add(fut);
    }
    assertWaitUntil(() -> count.get() == 5);
    try {
      awaitFuture(client.request(new RequestOptions().setServer(new FakeAddress("example.com")).setTimeout(100)));
    } catch (RuntimeException e) {
      assertTrue(e.getMessage().contains("timeout"));
    }
    FakeLoadBalancer.FakeEndpointMetrics endpoint = lb.endpoints().get(0);
    assertWaitUntil(() -> endpoint.metrics().size() == 6);
    FakeLoadBalancer.FakeMetric metric = endpoint.metrics().get(5);
    assertNotNull(metric.failure);
  }

  @Test
  public void testStatisticsReportingFailure1() throws Exception {
    FakeLoadBalancer.FakeMetric metric = testStatisticsReportingFailure((fail, req) -> {
      if (fail) {
        req.connection().close();
      } else {
        req.response().end();
      }
    }, req -> {
      req.setChunked(true);
      req.write("chunk");
      return req.response().compose(HttpClientResponse::body);
    });
    assertNotSame(0, metric.responseBegin());
    assertEquals(0, metric.requestEnd());
    assertEquals(0, metric.responseBegin());
    assertEquals(0, metric.responseEnd());
    assertNotNull(metric.failure());
    assertTrue(metric.failure() instanceof HttpClosedException);
  }

  @Test
  public void testStatisticsReportingFailure2() throws Exception {
    FakeLoadBalancer.FakeMetric metric = testStatisticsReportingFailure((fail, req) -> {
      if (fail) {
        req.connection().close();
      } else {
        req.response().end();
      }
    }, req -> req
      .send()
      .compose(HttpClientResponse::body));
    assertTrue(metric.requestEnd() - metric.requestBegin() >= 0);
    assertEquals(0, metric.responseBegin());
    assertEquals(0, metric.responseEnd());
    assertNotNull(metric.failure());
    assertTrue(metric.failure() instanceof HttpClosedException);
  }

  @Test
  public void testStatisticsReportingFailure3() throws Exception {
    FakeLoadBalancer.FakeMetric metric = testStatisticsReportingFailure((fail, req) -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true).write("chunk");
      vertx.setTimer(100, id -> {
        if (fail) {
          req.connection().close();
        } else {
          resp.end();
        }
      });
    }, req -> req
      .send()
      .compose(HttpClientResponse::body)
    );
    assertTrue(metric.requestEnd() - metric.requestBegin() >= 0);
    assertTrue(metric.responseBegin() - metric.requestEnd() >= 0);
    assertEquals(0, metric.responseEnd());
    assertNotNull(metric.failure());
    assertTrue(metric.failure() instanceof HttpClosedException);
  }

  private FakeLoadBalancer.FakeMetric testStatisticsReportingFailure(BiConsumer<Boolean, HttpServerRequest> handler, Function<HttpClientRequest, Future<Buffer>> sender) throws Exception {
    startServers(1);
    AtomicBoolean mode = new AtomicBoolean();
    requestHandler = (idx, req) -> handler.accept(mode.get(), req);
    FakeAddressResolver resolver = new FakeAddressResolver();
    resolver.registerAddress("example.com", Arrays.asList(SocketAddress.inetSocketAddress(8080, "localhost")));
    FakeLoadBalancer lb = new FakeLoadBalancer();
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .with(new HttpClientOptions().setKeepAliveTimeout(1))
      .withAddressResolver(resolver)
      .withLoadBalancer(lb)
      .build();
    List<Future<Buffer>> futures = new ArrayList<>();
    for (int i = 0;i < 10;i++) {
      Future<Buffer> fut = client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
        .send()
        .andThen(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
        }))
        .compose(HttpClientResponse::body));
      futures.add(fut);
    }
    awaitFuture(Future.join(futures));
    mode.set(true);
    try {
      awaitFuture(client
        .request(new RequestOptions().setServer(new FakeAddress("example.com")))
        .compose(sender::apply));
    } catch (RuntimeException e) {
      assertTrue(e.getMessage().contains("Connection was closed"));
    }
    FakeLoadBalancer.FakeEndpointMetrics endpoint = lb.endpoints().get(0);
    assertWaitUntil(() -> endpoint.metrics().size() == 11);
    for (int i = 0;i < 10;i++) {
      FakeLoadBalancer.FakeMetric metric = endpoint.metrics().get(i);
      assertTrue(metric.requestEnd() - metric.requestBegin() >= 0);
      assertTrue(metric.responseBegin() - metric.requestEnd() >= 0);
      assertTrue(metric.responseEnd() - metric.responseBegin() >= 0);
    }
    return endpoint.metrics().get(10);
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
  public void testTimeExpiration() throws Exception {
    startServers(1);
    requestHandler = (idx, req) -> {
      req.response().end("" + idx);
    };
    FakeAddressResolver resolver = new FakeAddressResolver();
    SocketAddress addr1 = SocketAddress.inetSocketAddress(8080, "localhost");
    resolver.registerAddress("example.com", Arrays.asList(addr1));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .with(new HttpClientOptions().setKeepAliveTimeout(1))
      .withAddressResolver(resolver)
      .build();
    String res = awaitFuture(client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
      .send()
      .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
      .compose(HttpClientResponse::body)
    )).toString();
    assertEquals("0", res);
    waitUntil(() -> resolver.endpoints("example.com") == null);
  }

  @Test
  public void testTimeRefreshExpiration() throws Exception {
    startServers(1);
    requestHandler = (idx, req) -> {
      req.response().end("" + idx);
    };
    FakeAddressResolver resolver = new FakeAddressResolver();
    SocketAddress addr1 = SocketAddress.inetSocketAddress(8080, "localhost");
    resolver.registerAddress("example.com", Arrays.asList(addr1));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .with(new HttpClientOptions().setKeepAliveTimeout(2))
      .withAddressResolver(resolver)
      .build();
    int numRequests = 20; // 200ms x 20 = 4 seconds
    for (int i = 0;i < numRequests;i++) {
      String res = awaitFuture(client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
        .send()
        .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
        .compose(HttpClientResponse::body)
      )).toString();
      assertEquals("0", res);
      Thread.sleep(200);
    }
    assertNotNull(resolver.endpoints("example.com"));
    waitUntil(() -> resolver.endpoints("example.com") == null);
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
