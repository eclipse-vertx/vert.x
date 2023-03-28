package io.vertx.core.http;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.HttpClientInternal;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.resolver.AddressResolver;
import io.vertx.test.core.VertxTestBase;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;

public class ResolvingHttpClientTest extends VertxTestBase {

  private BiConsumer<Integer, HttpServerRequest> requestHandler;
  private List<HttpServer> servers;

  private void startServers(int numServers) throws Exception {
    if (servers != null) {
      throw new IllegalStateException();
    }
    servers = new ArrayList<>();
    for (int i = 0;i < numServers;i++) {
      int val = i;
      HttpServer server = vertx.createHttpServer().requestHandler(req -> {
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
    final List<SocketAddress> addresses;
    int index;
    final List<FakeMetric> metrics = Collections.synchronizedList(new ArrayList<>());
    FakeState(String name, List<SocketAddress> addresses) {
      this.name = name;
      this.addresses = addresses;
    }
    FakeState(String name, SocketAddress... addresses) {
      this.name = name;
      this.addresses = new ArrayList<>(Arrays.asList(addresses));
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

  static class FakeMetric {
    final SocketAddress server;
    long requestBegin;
    long requestEnd;
    long responseBegin;
    long responseEnd;
    FakeMetric(SocketAddress server) {
      this.server = server;
    }
  }

  private static class FixedAddressResolver implements AddressResolver<FakeState, FakeAddress, FakeMetric> {

    private final ConcurrentMap<String, FakeState> map = new ConcurrentHashMap<>();

    @Override
    public FakeMetric requestBegin(FakeState state, SocketAddress address) {
      FakeMetric metric = new FakeMetric(address);
      metric.requestBegin = System.currentTimeMillis();
      state.metrics.add(metric);
      return metric;
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
    public Future<SocketAddress> pickAddress(FakeState state) {
      int idx = state.index++;
      SocketAddress result = state.addresses.get(idx % state.addresses.size());
      return Future.succeededFuture(result);
    }

    @Override
    public void removeAddress(FakeState state, SocketAddress address) {
      state.addresses.remove(address);
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
    HttpClientInternal client = (HttpClientInternal) vertx.createHttpClient();
    FixedAddressResolver resolver = new FixedAddressResolver();
    resolver.map.put("example.com", new FakeState("example.com", SocketAddress.inetSocketAddress(8080, "localhost"), SocketAddress.inetSocketAddress(8081, "localhost")));
    client.addressResolver(resolver);
    Set<String> responses = Collections.synchronizedSet(new HashSet<>());
    for (int i = 0;i < numServers * 2;i++) {
      client.request(new FakeAddress("example.com"), HttpMethod.GET, 8080, "example.com", "/").compose(req -> req
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
    HttpClientInternal client = (HttpClientInternal) vertx.createHttpClient();
    FixedAddressResolver resolver = new FixedAddressResolver();
    resolver.map.put("example.com", new FakeState("example.com", SocketAddress.inetSocketAddress(8080, "localhost"), SocketAddress.inetSocketAddress(8081, "localhost")));
    client.addressResolver(resolver);
    client.request(new FakeAddress("example.com"), HttpMethod.GET, 8080, "example.com", "/").compose(req -> req
      .send()
      .compose(HttpClientResponse::body)
    ).transform(ar -> {
      if (ar.failed()) {
        List<SocketAddress> addresses = resolver.map.get("example.com").addresses;
        assertEquals(Collections.singletonList(SocketAddress.inetSocketAddress(8081, "localhost")), addresses);
        return client.request(HttpMethod.GET, 8080, "example.com", "/").compose(req -> req
          .send()
          .compose(HttpClientResponse::body));
      } else {
        return Future.succeededFuture();
      }
    }).onComplete(onFailure(err -> {
      assertTrue(resolver.map.isEmpty());
      testComplete();
    }));
    await();
  }

  @Test
  public void testShutdownServers() throws Exception {
    int numServers = 4;
    requestHandler = (idx, req) -> req.response().end("server-" + idx);
    startServers(numServers);
    FixedAddressResolver resolver = new FixedAddressResolver();
    resolver.map.put("example.com", new FakeState("example.com",
      SocketAddress.inetSocketAddress(8080, "localhost"),
      SocketAddress.inetSocketAddress(8081, "localhost"),
      SocketAddress.inetSocketAddress(8082, "localhost"),
      SocketAddress.inetSocketAddress(8083, "localhost")
    ));
    HttpClientInternal client = (HttpClientInternal) vertx.createHttpClient();
    client.addressResolver(resolver);
    CountDownLatch latch = new CountDownLatch(numServers);
    for (int i = 0;i < numServers;i++) {
      client.request(new FakeAddress("example.com"), HttpMethod.GET, 8080, "example.com", "/").compose(req -> req
        .send()
        .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
        .compose(HttpClientResponse::body)
      ).onComplete(onSuccess(v -> {
        latch.countDown();
      }));
    }
    awaitLatch(latch);
    FakeState state = resolver.map.get("example.com");
    for (int i = 0;i < servers.size();i++) {
      int expected = state.addresses.size() - 1;
      awaitFuture(servers.get(i).close());
      assertWaitUntil(() -> state.addresses.size() == expected);
    }
    assertWaitUntil(resolver.map::isEmpty);
  }

  @Test
  public void testResolveToSameSocketAddress() throws Exception {
    requestHandler = (idx, req) -> req.response().end("server-" + idx);
    startServers(1);
    FixedAddressResolver resolver = new FixedAddressResolver();
    SocketAddress address = SocketAddress.inetSocketAddress(8080, "localhost");
    resolver.map.put("server1.com", new FakeState("server1.com", address));
    resolver.map.put("server2.com", new FakeState("server2.com", address));
    HttpClientInternal client = (HttpClientInternal) vertx.createHttpClient();
    client.addressResolver(resolver);
    Future<Buffer> result = client.request(new FakeAddress("server1.com"), HttpMethod.GET, 8080, "server1.com", "/").compose(req -> req
      .send()
      .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
      .compose(HttpClientResponse::body)
    ).compose(body -> client
      .request(new FakeAddress("server2.com"), HttpMethod.GET, 8080, "server2.com", "/")
      .compose(req -> req
        .send()
        .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
        .compose(HttpClientResponse::body)
      ));
    awaitFuture(result);
    awaitFuture(servers.get(0).close());
    assertWaitUntil(() -> resolver.map.keySet().isEmpty());
  }

  @Test
  public void testResolveFailure() {
    HttpClientInternal client = (HttpClientInternal) vertx.createHttpClient();
    Exception cause = new Exception("Not found");
    FixedAddressResolver resolver = new FixedAddressResolver() {
      @Override
      public Future<FakeState> resolve(FakeAddress address) {
        return Future.failedFuture(cause);
      }
    };
    client.addressResolver(resolver);
    client.request(new FakeAddress("foo.com"), HttpMethod.GET, 8080, "foo.com", "/").compose(req -> req
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
    HttpClientInternal client = (HttpClientInternal) vertx.createHttpClient();
    FixedAddressResolver resolver = new FixedAddressResolver();
    client.addressResolver(resolver);
    client.request(new Address() {
    }, HttpMethod.GET, 8080, "foo.com", "/").compose(req -> req
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
  public void testUseSocketAddressAddress() throws Exception {
    requestHandler = (idx, req) -> req.response().end("server");
    startServers(1);
    HttpClientInternal client = (HttpClientInternal) vertx.createHttpClient();
    FixedAddressResolver resolver = new FixedAddressResolver();
    resolver.map.put("example.com", new FakeState("example.com", SocketAddress.inetSocketAddress(8080, "localhost")));
    client.addressResolver(resolver);
    client.request(SocketAddress.inetSocketAddress(servers.get(0).actualPort(), "localhost"), HttpMethod.GET, 8080, "foo.com", "/").compose(req -> req
      .send()
      .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
      .compose(HttpClientResponse::body)
    ).onComplete(onSuccess(body -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testKeepAliveTimeout() throws Exception {
    startServers(1);
    requestHandler = (idx, req) -> req.response().end("server-" + idx);
    HttpClientInternal client = (HttpClientInternal) vertx.createHttpClient(new HttpClientOptions().setKeepAliveTimeout(1));
    FixedAddressResolver resolver = new FixedAddressResolver();
    resolver.map.put("example.com", new FakeState("example.com", SocketAddress.inetSocketAddress(8080, "localhost")));
    client.addressResolver(resolver);
    CountDownLatch closedLatch = new CountDownLatch(1);
    client.connectionHandler(conn -> {
      conn.closeHandler(v -> {
        closedLatch.countDown();
      });
    });
    client.request(new FakeAddress("example.com"), HttpMethod.GET, 8080, "example.com", "/").compose(req -> req
      .send()
      .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
      .compose(HttpClientResponse::body)
    ).onComplete(onSuccess(v -> {
    }));
    awaitLatch(closedLatch);
    assertWaitUntil(() -> resolver.map.keySet().isEmpty());
  }

  @Test
  public void testStatistics() throws Exception {
    startServers(1);
    requestHandler = (idx, req) -> {
      vertx.setTimer(500, id -> {
        req.response().end();
      });
    };
    HttpClientInternal client = (HttpClientInternal) vertx.createHttpClient(new HttpClientOptions().setKeepAliveTimeout(1));
    FixedAddressResolver resolver = new FixedAddressResolver();
    SocketAddress addr = SocketAddress.inetSocketAddress(8080, "localhost");
    resolver.map.put("example.com", new FakeState("example.com", addr));
    client.addressResolver(resolver);
    awaitFuture(client.request(new FakeAddress("example.com"), HttpMethod.GET, 8080, "example.com", "/").compose(req -> req
      .send()
      .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
      .compose(HttpClientResponse::body)
    ));
    FakeState state = resolver.map.get("example.com");
    assertWaitUntil(() -> state.metrics.size() == 1);
    FakeMetric metric = state.metrics.get(0);
    assertEquals(addr, metric.server);
    assertTrue(metric.requestEnd - metric.requestBegin >= 0);
    assertTrue(metric.responseBegin - metric.requestEnd > 500);
    assertTrue(metric.responseEnd - metric.responseBegin >= 0);
  }
}
