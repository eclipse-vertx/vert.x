package io.vertx.core.http;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.HttpClientInternal;
import io.vertx.core.net.*;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.proxy.HttpProxy;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

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

  static class FakeState {
    final String name;
    final List<FakeEndpoint> endpoints;
    int index;
    final List<FakeMetric> metrics = Collections.synchronizedList(new ArrayList<>());
    volatile boolean valid;
    FakeState(String name, List<SocketAddress> endpoints) {
      this.name = name;
      this.endpoints = endpoints.stream().map(s -> new FakeEndpoint(this, s)).collect(Collectors.toList());
      this.valid = true;
    }
    FakeState(String name, SocketAddress... endpoints) {
      this(name, Arrays.asList(endpoints));
    }
    List<SocketAddress> addresses() {
      return endpoints.stream().map(e -> e.address).collect(Collectors.toList());
    }
  }

  static class FakeAddress implements Address {

    private final String name;

    public FakeAddress(String name) {
      this.name = name;
    }

    public String name() {
      return name;
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof FakeAddress) {
        FakeAddress that = (FakeAddress) obj;
        return name.equals(that.name);
      }
      return false;
    }

    @Override
    public String toString() {
      return "ServiceName(" + name + ")";
    }
  }

  static class FakeEndpoint {
    final FakeState state;
    final SocketAddress address;
    FakeEndpoint(FakeState state, SocketAddress address) {
      this.state = state;
      this.address = address;
    }
  }

  static class FakeMetric {
    final FakeEndpoint endpoint;
    long requestBegin;
    long requestEnd;
    long responseBegin;
    long responseEnd;
    FakeMetric(FakeEndpoint endpoint) {
      this.endpoint = endpoint;
    }
  }

  private static class FixedAddressResolver implements AddressResolver, io.vertx.core.spi.net.AddressResolver<FakeState, FakeAddress, FakeMetric, FakeEndpoint> {

    private final ConcurrentMap<String, FakeState> map = new ConcurrentHashMap<>();

    @Override
    public io.vertx.core.spi.net.AddressResolver<?, ?, ?, ?> resolver(Vertx vertx) {
      return this;
    }

    @Override
    public boolean isValid(FakeState state) {
      return state.valid;
    }

    @Override
    public FakeMetric requestBegin(FakeEndpoint endpoint) {
      FakeMetric metric = new FakeMetric(endpoint);
      metric.requestBegin = System.currentTimeMillis();
      endpoint.state.metrics.add(metric);
      return metric;
    }

    @Override
    public SocketAddress addressOf(FakeEndpoint endpoint) {
      return endpoint.address;
    }

    @Override
    public void requestEnd(FakeMetric metric) {
      metric.requestEnd = System.currentTimeMillis();
    }

    @Override
    public void responseBegin(FakeMetric metric) {
      metric.responseBegin = System.currentTimeMillis();
    }

    @Override
    public void responseEnd(FakeMetric metric) {
      metric.responseEnd = System.currentTimeMillis();
    }

    @Override
    public FakeAddress tryCast(Address address) {
      return address instanceof FakeAddress ? (FakeAddress) address : null;
    }

    @Override
    public Future<FakeState> resolve(FakeAddress address) {
      FakeState state = map.get(address.name());
      if (state != null) {
        return Future.succeededFuture(state);
      } else {
        return Future.failedFuture("Could not resolve " + address);
      }
    }

    @Override
    public Future<FakeEndpoint> pickEndpoint(FakeState state) {
      int idx = state.index++;
      FakeEndpoint result = state.endpoints.get(idx % state.endpoints.size());
      return Future.succeededFuture(result);
    }

    @Override
    public void removeAddress(FakeState state, FakeEndpoint endpoint) {
      state.endpoints.remove(endpoint);
    }

    @Override
    public void dispose(FakeState state) {
      map.remove(state.name);
    }
  }

  @Test
  public void testResolveServers() throws Exception {
    int numServers = 2;
    waitFor(numServers * 2);
    startServers(numServers);
    requestHandler = (idx, req) -> req.response().end("server-" + idx);
    FixedAddressResolver resolver = new FixedAddressResolver();
    resolver.map.put("example.com", new FakeState("example.com", SocketAddress.inetSocketAddress(8080, "localhost"), SocketAddress.inetSocketAddress(8081, "localhost")));
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
    FixedAddressResolver lookup = new FixedAddressResolver();
    lookup.map.put("example.com", new FakeState("example.com", SocketAddress.inetSocketAddress(8080, "localhost"), SocketAddress.inetSocketAddress(8081, "localhost")));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(lookup)
      .build();
    client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
      .send()
      .compose(HttpClientResponse::body)
    ).transform(ar -> {
      if (ar.failed()) {
        List<SocketAddress> addresses = lookup.map.get("example.com").addresses();
        assertEquals(Collections.singletonList(SocketAddress.inetSocketAddress(8081, "localhost")), addresses);
        return client.request(HttpMethod.GET, 8080, "example.com", "/").compose(req -> req
          .send()
          .compose(HttpClientResponse::body));
      } else {
        return Future.succeededFuture();
      }
    }).onComplete(onFailure(err -> {
      assertTrue(lookup.map.isEmpty());
      testComplete();
    }));
    await();
  }

  @Test
  public void testShutdownServers() throws Exception {
    int numServers = 4;
    requestHandler = (idx, req) -> req.response().end("server-" + idx);
    startServers(numServers);
    FixedAddressResolver lookup = new FixedAddressResolver();
    lookup.map.put("example.com", new FakeState("example.com",
      SocketAddress.inetSocketAddress(8080, "localhost"),
      SocketAddress.inetSocketAddress(8081, "localhost"),
      SocketAddress.inetSocketAddress(8082, "localhost"),
      SocketAddress.inetSocketAddress(8083, "localhost")
    ));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(lookup)
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
    FakeState state = lookup.map.get("example.com");
    for (int i = 0;i < servers.size();i++) {
      int expected = state.endpoints.size() - 1;
      awaitFuture(servers.get(i).close());
      assertWaitUntil(() -> state.endpoints.size() == expected);
    }
    assertWaitUntil(lookup.map::isEmpty);
  }

  @Test
  public void testResolveToSameSocketAddress() throws Exception {
    requestHandler = (idx, req) -> req.response().end("server-" + idx);
    startServers(1);
    FixedAddressResolver lookup = new FixedAddressResolver();
    SocketAddress address = SocketAddress.inetSocketAddress(8080, "localhost");
    lookup.map.put("server1.com", new FakeState("server1.com", address));
    lookup.map.put("server2.com", new FakeState("server2.com", address));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(lookup)
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
    assertWaitUntil(() -> lookup.map.keySet().isEmpty());
  }

  @Test
  public void testResolveToSameSocketAddressWithProxy() throws Exception {
    requestHandler = (idx, req) -> req.response().end("server-" + idx);
    startServers(1);

    HttpProxy proxy = new HttpProxy();
    proxy.start(vertx);

    FixedAddressResolver lookup = new FixedAddressResolver();
    SocketAddress address = SocketAddress.inetSocketAddress(8080, "localhost");
    lookup.map.put("example.com", new FakeState("example.com", address));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(lookup)
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
    FixedAddressResolver lookup = new FixedAddressResolver() {
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
    FixedAddressResolver lookup = new FixedAddressResolver();
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
    FixedAddressResolver lookup = new FixedAddressResolver();
    lookup.map.put("example.com", new FakeState("example.com", SocketAddress.inetSocketAddress(8080, "localhost")));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .with(new HttpClientOptions().setKeepAliveTimeout(1))
      .withConnectHandler(conn -> {
        conn.closeHandler(v -> {
          closedLatch.countDown();
        });
      })
      .withAddressResolver(lookup)
      .build();
    client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
      .send()
      .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
      .compose(HttpClientResponse::body)
    ).onComplete(onSuccess(v -> {
    }));
    awaitLatch(closedLatch);
    assertWaitUntil(() -> lookup.map.keySet().isEmpty());
  }

  @Test
  public void testStatistics() throws Exception {
    startServers(1);
    requestHandler = (idx, req) -> {
      vertx.setTimer(500, id -> {
        req.response().end();
      });
    };
    FixedAddressResolver lookup = new FixedAddressResolver();
    SocketAddress addr = SocketAddress.inetSocketAddress(8080, "localhost");
    lookup.map.put("example.com", new FakeState("example.com", addr));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .with(new HttpClientOptions().setKeepAliveTimeout(1))
      .withAddressResolver(lookup)
      .build();
    awaitFuture(client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
      .send()
      .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
      .compose(HttpClientResponse::body)
    ));
    FakeState state = lookup.map.get("example.com");
    assertWaitUntil(() -> state.metrics.size() == 1);
    FakeMetric metric = state.metrics.get(0);
    assertEquals(addr, metric.endpoint.address);
    assertTrue(metric.requestEnd - metric.requestBegin >= 0);
    assertTrue(metric.responseBegin - metric.requestEnd > 500);
    assertTrue(metric.responseEnd - metric.responseBegin >= 0);
  }

  @Test
  public void testInvalidation() throws Exception {
    startServers(2);
    requestHandler = (idx, req) -> {
      req.response().end("" + idx);
    };
    FixedAddressResolver lookup = new FixedAddressResolver();
    SocketAddress addr1 = SocketAddress.inetSocketAddress(8080, "localhost");
    SocketAddress addr2 = SocketAddress.inetSocketAddress(8081, "localhost");
    lookup.map.put("example.com", new FakeState("example.com", addr1));
    HttpClientInternal client = (HttpClientInternal) vertx.httpClientBuilder()
      .withAddressResolver(lookup)
      .build();
    String res = awaitFuture(client.request(new RequestOptions().setServer(new FakeAddress("example.com"))).compose(req -> req
      .send()
      .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
      .compose(HttpClientResponse::body)
    )).toString();
    assertEquals("0", res);
    lookup.map.put("example.com", new FakeState("example.com", addr2)).valid = false;
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
    FixedAddressResolver lookup = new FixedAddressResolver();
    SocketAddress addr1 = SocketAddress.inetSocketAddress(8080, "localhost");
    lookup.map.put("example.com", new FakeState("example.com", addr1));
    HttpClient client = vertx.httpClientBuilder()
      .with(new HttpClientOptions()
        .setSsl(true)
        .setTrustOptions(Trust.SERVER_JKS.get())
        .setVerifyHost(verifyHost)
      )
      .withAddressResolver(lookup)
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
