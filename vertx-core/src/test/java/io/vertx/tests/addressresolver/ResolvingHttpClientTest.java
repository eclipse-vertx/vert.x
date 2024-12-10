package io.vertx.tests.addressresolver;

import io.vertx.core.Future;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.CleanableHttpClient;
import io.vertx.core.http.impl.HttpClientImpl;
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.core.net.*;
import io.vertx.core.net.endpoint.ServerEndpoint;
import io.vertx.core.spi.endpoint.EndpointBuilder;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakeloadbalancer.FakeLoadBalancer;
import io.vertx.test.fakeresolver.*;
import io.vertx.test.http.HttpTestBase;
import io.vertx.test.proxy.HttpProxy;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ResolvingHttpClientTest extends VertxTestBase {

  private BiConsumer<Integer, HttpServerRequest> requestHandler;
  private List<HttpServer> servers;

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.getAddressResolverOptions().setHostsValue(Buffer.buffer("" +
      "127.0.0.1 localhost\n" +
      "127.0.0.1 s1.example.com\n" +
      "127.0.0.1 s2.example.com\n"
    ));
    return options;
  }

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
      awaitFuture(server.listen(HttpTestBase.DEFAULT_HTTP_PORT + i, "localhost"));
    }
  }

  @Test
  public void testResolveServers() throws Exception {
    int numServers = 2;
    waitFor(numServers * 2);
    startServers(numServers);
    requestHandler = (idx, req) -> req.response().end("server-" + idx);
    FakeEndpointResolver resolver = new FakeEndpointResolver();
    resolver.registerAddress("example.com", Arrays.asList(SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "localhost"), SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT + 1, "localhost")));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(resolver)
      .build();
    Set<String> responses = Collections.synchronizedSet(new HashSet<>());
    for (int i = 0;i < numServers * 2;i++) {
      client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
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
    FakeEndpointResolver resolver = new FakeEndpointResolver();
    resolver.registerAddress("example.com", Arrays.asList(
      SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "localhost"),
      SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT + 1, "localhost"),
      SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT + 2, "localhost"),
      SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT + 3, "localhost")
    ));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(resolver)
      .build();
    CountDownLatch latch = new CountDownLatch(numServers);
    for (int i = 0;i < numServers;i++) {
      client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
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
    FakeEndpointResolver resolver = new FakeEndpointResolver();
    SocketAddress address = SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "localhost");
    resolver.registerAddress("server1.com", List.of(address));
    resolver.registerAddress("server2.com", List.of(address));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(resolver)
      .build();
    Future<Buffer> result = client.request(new RequestOptions().setServer(new FakeAddress("server1.com"))).compose(req -> req
      .send()
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
    ).compose(body -> client
      .request(new RequestOptions().setServer(new FakeAddress("server2.com")))
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
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

    FakeEndpointResolver resolver = new FakeEndpointResolver();
    resolver.registerAddress("example.com", Arrays.asList(SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "localhost")));
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
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body)
      );
      Buffer response = awaitFuture(result);
      responses.add(response);
    }
    assertNotNull(proxy.lastLocalAddress());
  }

  @Test
  public void testAcceptProxyFilter() throws Exception {
    testFilter(true);
  }

  @Test
  public void testRejectProxyFilter() throws Exception {
    testFilter(false);
  }

  private void testFilter(boolean accept) throws Exception {
    HttpProxy proxy = new HttpProxy();
    proxy.start(vertx);
    try {
      int numServers = 2;
      waitFor(numServers * 2);
      startServers(numServers);
      requestHandler = (idx, req) -> {
        boolean proxied = idx == 0 && accept;
        SocketAddress remote = req.connection().remoteAddress();
        assertEquals(proxied, proxy.localAddresses().contains(remote.host() + ":" + remote.port()));
        req.response().end("server-" + idx);
      };
      FakeEndpointResolver resolver = new FakeEndpointResolver();
      resolver.registerAddress("example.com", Arrays.asList(
        SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "s1.example.com"),
        SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT + 1, "s2.example.com")));
      HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
        .with(new HttpClientOptions()
          .setProxyOptions(new ProxyOptions().setType(ProxyType.HTTP).setHost("localhost").setPort(proxy.port())))
        .withAddressResolver(resolver)
        .build();
      ((HttpClientImpl)((CleanableHttpClient)client).delegate).proxyFilter(so -> {
        return accept && so.host().equals("s1.example.com");
      });
      Set<String> responses = Collections.synchronizedSet(new HashSet<>());
      for (int i = 0;i < numServers * 2;i++) {
        client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
          .send()
          .expecting(HttpResponseExpectation.SC_OK)
          .compose(HttpClientResponse::body)
        ).onComplete(onSuccess(v -> {
          responses.add(v.toString());
          complete();
        }));
      }
      await();
      Assert.assertEquals(new HashSet<>(Arrays.asList("server-0", "server-1")), responses);
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testResolveFailure() {
    Exception cause = new Exception("Not found");
    FakeEndpointResolver lookup = new FakeEndpointResolver() {
      @Override
      public Future<FakeState> resolve(FakeAddress address, EndpointBuilder builder) {
        return Future.failedFuture(cause);
      }
    };
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(lookup)
      .build();
    client.request(new RequestOptions().setServer(new FakeAddress("foo.com"))).compose(req -> req
      .send()
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
    ).onComplete(onFailure(err -> {
      assertSame(cause, err);
      testComplete();
    }));
    await();
  }

  @Test
  public void testUseInvalidAddress() {
    FakeEndpointResolver lookup = new FakeEndpointResolver();
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(lookup)
      .build();
    client.request(new RequestOptions().setServer(new Address() {
    })).compose(req -> req
      .send()
      .expecting(HttpResponseExpectation.SC_OK)
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
    FakeEndpointResolver resolver = new FakeEndpointResolver();
    resolver.registerAddress("example.com", Arrays.asList(SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "localhost")));
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
      .expecting(HttpResponseExpectation.SC_OK)
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
    FakeEndpointResolver resolver = new FakeEndpointResolver();
    resolver.registerAddress("example.com", Arrays.asList(SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "localhost")));
    FakeLoadBalancer lb = new FakeLoadBalancer();
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .with(new HttpClientOptions().setKeepAliveTimeout(1))
      .withAddressResolver(resolver)
      .withLoadBalancer(lb)
      .build();
    client.request(new RequestOptions().setServer(new FakeAddress("example.com")))
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body)
      ).await();
    FakeLoadBalancer.FakeLoadBalancerMetrics<?> endpoint = (FakeLoadBalancer.FakeLoadBalancerMetrics<?>) ((ServerEndpoint) lb.endpoints().get(0)).metrics();
    FakeLoadBalancer.FakeMetric metric = endpoint.metrics2().get(0);
    assertTrue(metric.requestEnd() - metric.requestBegin() >= 0);
    assertTrue(metric.responseBegin() - metric.requestEnd() >= 500);
    assertTrue(metric.responseEnd() - metric.responseBegin() >= 0);
  }

  @Test
  public void testStatisticsReportingFailure0() throws Exception {
    startServers(1);
    AtomicInteger count = new AtomicInteger();
    requestHandler = (idx, req) -> {
      count.incrementAndGet();
    };
    FakeEndpointResolver resolver = new FakeEndpointResolver();
    FakeLoadBalancer lb = new FakeLoadBalancer();
    resolver.registerAddress("example.com", Arrays.asList(SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "localhost")));
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
    } catch (Throwable e) {
      assertTrue(e.getMessage().contains("timeout"));
    }
    FakeLoadBalancer.FakeLoadBalancerMetrics<?> endpoint = (FakeLoadBalancer.FakeLoadBalancerMetrics) lb.endpoints().get(0).metrics();
    assertWaitUntil(() -> endpoint.metrics2().size() == 6);
    FakeLoadBalancer.FakeMetric metric = endpoint.metrics2().get(5);
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
    FakeEndpointResolver resolver = new FakeEndpointResolver();
    resolver.registerAddress("example.com", Arrays.asList(SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "localhost")));
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
        .expecting(HttpResponseExpectation.SC_OK)
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
    FakeLoadBalancer.FakeLoadBalancerMetrics<?> endpoint = (FakeLoadBalancer.FakeLoadBalancerMetrics) (lb.endpoints().get(0).metrics());
    assertWaitUntil(() -> endpoint.metrics2().size() == 11);
    for (int i = 0;i < 10;i++) {
      FakeLoadBalancer.FakeMetric metric = endpoint.metrics2().get(i);
      assertTrue(metric.requestEnd() - metric.requestBegin() >= 0);
      assertTrue(metric.responseBegin() - metric.requestEnd() >= 0);
      assertTrue(metric.responseEnd() - metric.responseBegin() >= 0);
    }
    return endpoint.metrics2().get(10);
  }

  @Test
  public void testInvalidation() throws Exception {
    startServers(2);
    requestHandler = (idx, req) -> {
      req.response().end("" + idx);
    };
    FakeEndpointResolver resolver = new FakeEndpointResolver();
    SocketAddress addr1 = SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "localhost");
    SocketAddress addr2 = SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT + 1, "localhost");
    resolver.registerAddress("example.com", Arrays.asList(addr1));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(resolver)
      .build();
    String res = awaitFuture(client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
      .send()
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
    )).toString();
    assertEquals("0", res);
    resolver.registerAddress("example.com", List.of(addr2));
    try {
      awaitFuture(client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body)
      ));
    } catch (Throwable e) {
      assertTrue(e.getMessage().startsWith("Cannot resolve address"));
    }
  }

  @Test
  public void testDelayedInvalidation() throws Exception {
    testInvalidation(false);
  }

  @Test
  public void testImmediateInvalidation() throws Exception {
    testInvalidation(true);
  }

  private void testInvalidation(boolean immediate) throws Exception {
    startServers(2);
    requestHandler = (idx, req) -> {
      req.response().end("" + idx);
    };
    FakeEndpointResolver resolver = new FakeEndpointResolver();
    SocketAddress addr1 = SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "localhost");
    SocketAddress addr2 = SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT + 1, "localhost");
    AtomicInteger accessCount = new AtomicInteger();
    resolver.registerAddress("example.com", () -> {
      accessCount.incrementAndGet();
      return new FakeEndpointResolver.Endpoint(List.of(addr1), !immediate);
    });
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(resolver)
      .build();
    String res = awaitFuture(client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
      .send()
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
    )).toString();
    assertEquals("0", res);
    assertEquals(1, accessCount.get());
    res = awaitFuture(client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
      .send()
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
    )).toString();
    assertEquals("0", res);
    assertEquals(immediate ? 2 : 1, accessCount.get());
    resolver.registerAddress("example.com", List.of(addr2));
    res = awaitFuture(client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
      .send()
      .expecting(HttpResponseExpectation.SC_OK)
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
    FakeEndpointResolver resolver = new FakeEndpointResolver();
    SocketAddress addr1 = SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "localhost");
    resolver.registerAddress("example.com", Arrays.asList(addr1));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .with(new HttpClientOptions().setKeepAliveTimeout(1))
      .withAddressResolver(resolver)
      .build();
    String res = awaitFuture(client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
      .send()
      .expecting(HttpResponseExpectation.SC_OK)
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
    FakeEndpointResolver resolver = new FakeEndpointResolver();
    SocketAddress addr1 = SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "localhost");
    resolver.registerAddress("example.com", Arrays.asList(addr1));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .with(new HttpClientOptions().setKeepAliveTimeout(2))
      .withAddressResolver(resolver)
      .build();
    int numRequests = 20; // 200ms x 20 = 4 seconds
    for (int i = 0;i < numRequests;i++) {
      String res = awaitFuture(client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
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
      .setPort(HttpTestBase.DEFAULT_HTTP_PORT)
      .setURI("/"), true, true);
  }

  @Test
  public void testSSLOverridePeerNoVerify() throws Exception {
    testSSL(new RequestOptions()
      .setMethod(HttpMethod.GET)
      .setServer(new FakeAddress("example.com"))
      .setHost("example.com")
      .setPort(HttpTestBase.DEFAULT_HTTP_PORT)
      .setURI("/"), false, false);
  }

  private void testSSL(RequestOptions request, boolean expectFailure, boolean verifyHost) throws Exception {
    startServers(1, new HttpServerOptions()
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS.get()));
    requestHandler = (idx, req) -> {
      req.response().end("" + idx);
    };
    FakeEndpointResolver resolver = new FakeEndpointResolver();
    SocketAddress addr1 = SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "localhost");
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
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body)
      )).toString();
      assertFalse(expectFailure);
      assertEquals("0", res);
    } catch (Exception e) {
      assertTrue(expectFailure);
    }
  }

  @Test
  public void testConsistentHashing() throws Exception {
    int numServers = 4;
    int numClients = 10;
    int numRequests = 4;
    waitFor(numClients * numRequests);
    startServers(numServers);
    requestHandler = (idx, req) -> req.response().end("server-" + idx);
    FakeEndpointResolver resolver = new FakeEndpointResolver();
    List<SocketAddress> servers = IntStream
      .range(0, numServers)
      .mapToObj(idx -> SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT + idx, "localhost"))
      .collect(Collectors.toList());
    resolver.registerAddress("example.com", servers);
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withLoadBalancer(LoadBalancer.CONSISTENT_HASHING)
      .withAddressResolver(resolver)
      .build();
    Map<Integer, List<String>> responses = new ConcurrentHashMap<>();
    for (int i = 0;i < numClients * numRequests;i++) {
      int idx = i % numClients;
      String hashingKey = "client-" + idx;
      client.request(new RequestOptions().setServer(new FakeAddress("example.com")).setRoutingKey(hashingKey)).compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body)
      ).onComplete(onSuccess(v -> {
        responses.compute(idx, (id, list) -> {
          if (list == null) {
            list = Collections.synchronizedList(new ArrayList<>());
          }
          return list;
        }).add(v.toString());
        complete();
      }));
    }
    await();
    responses.values().forEach(list -> {
      String resp = list.get(0);
      for (int i = 1;i < list.size();i++) {
        Assert.assertEquals(resp, list.get(i));
      }
    });
  }

  @Test
  public void testLyingLoadBalancer() throws Exception {
    int numServers = 2;
    startServers(numServers);
    requestHandler = (idx, req) -> req.response().end("server-" + idx);
    FakeEndpointResolver resolver = new FakeEndpointResolver();
    resolver.registerAddress("example.com", Arrays.asList(SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "localhost"), SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT + 1, "localhost")));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withLoadBalancer(endpoints -> () -> endpoints.size() + 1)
      .withAddressResolver(resolver)
      .build();
    Set<String> responses = Collections.synchronizedSet(new HashSet<>());
    client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
      .send()
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
    ).onComplete(onFailure(err -> {
      assertEquals("No results for ServiceName(example.com)", err.getMessage());
      testComplete();
    }));
    await();
  }
}
