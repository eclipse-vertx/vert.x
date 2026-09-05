package io.vertx.tests.http;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.http.*;
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.core.internal.net.endpoint.EndpointResolverInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.VertxRunner;
import io.vertx.test.fakedns.DnsRecord;
import io.vertx.test.fakedns.DnsServer;
import io.vertx.test.fakedns.MockDnsServer;
import io.vertx.test.fakedns.WithDnsServer;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.ConnectException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.test.core.TestUtils.assertWaitUntil;
import static io.vertx.test.http.AbstractHttpTest.DEFAULT_HTTP_HOST;
import static io.vertx.test.http.AbstractHttpTest.DEFAULT_HTTP_PORT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(VertxRunner.class)
public class HttpClientResolverTest {

  @Rule
  public DnsServer dnsServer = new DnsServer();

  public HttpClientResolverTest() {
  }

  @WithDnsServer(records = {@DnsRecord(name = "vertx.io", address = "127.0.0.1"), @DnsRecord(name = "vertx.io", address = "127.0.0.2")})
  @Test
  public void testDnsClientSideLoadBalancingDisabled() throws Exception {
    Vertx vertx = Vertx.vertx(dnsServer.vertxOptions());
    try {
      testDnsClientSideLoadBalancing(vertx, false);
    } finally {
      vertx.close().await();
    }
  }

  @WithDnsServer(records = {@DnsRecord(name = "vertx.io", address = "127.0.0.1"), @DnsRecord(name = "vertx.io", address = "127.0.0.2")})
  @Test
  public void testDnsClientSideLoadBalancingEnabled() throws Exception {
    Vertx vertx = Vertx.vertx(dnsServer.vertxOptions());
    try {
      testDnsClientSideLoadBalancing(vertx, true);
    } finally {
      vertx.close().await();
    }
  }

  private void testDnsClientSideLoadBalancing(Vertx vertx, boolean enabled) {
    List<String> hosts = List.of("127.0.0.1", "127.0.0.2");
    List<String> actualHosts = new ArrayList<>();
    for (String host : hosts) {
      try {
        HttpServer server = vertx
          .createHttpServer()
          .requestHandler(request -> request.response().end())
          .listen(DEFAULT_HTTP_PORT, host)
          .await();
        actualHosts.add(host);
      } catch (Exception e) {
        // Could be a bind error on MacOS or Windows
        // on MacOS : 'sudo ifconfig lo0 alias 127.0.0.2 up'
      }
    }
    AtomicReference<Set<String>> balancedHosts = new AtomicReference<>();
    AtomicInteger idx = new AtomicInteger();
    HttpClient client = vertx
      .httpClientBuilder()
      .with(new HttpClientConfig().setConnectTimeout(Duration.ofMillis(500)))
      .withLoadBalancer(enabled ? endpoints -> () -> {
        balancedHosts.set(endpoints
          .stream()
          .map(se -> se.address().hostAddress())
          .collect(Collectors.toSet()));
        return idx.getAndIncrement() % endpoints.size();
      } : null)
      .build();
    Set<String> ipAdresses = new HashSet<>();
    for (int i = 0;i < hosts.size();i++) {
      SocketAddress addr = client
        .request(GET, DEFAULT_HTTP_PORT, "vertx.io", "/")
        .compose(r -> {
          return r.send().compose(resp -> resp.end().map(r.connection().remoteAddress()));
        })
        .await();
      ipAdresses.add(addr.hostAddress());
    }
    if (enabled) {
      Assertions.assertThat(ipAdresses).containsAll(actualHosts);
      Assertions.assertThat(balancedHosts.get()).containsAll(hosts);
    } else {
      assertEquals(Set.of("127.0.0.1"), ipAdresses);
    }
  }

  @Test
  public void testKeepAliveTimeout(Vertx vertx) {

    HttpServer server = vertx
      .createHttpServer()
      .requestHandler(request -> {
        request.response().end();
      });
    server
      .listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST)
      .await();

    HttpClientConfig clientConfig = new HttpClientConfig().setResolverConfig(new ClientResolverConfig().setKeepAliveTimeout(Duration.ofMillis(50)));
    HttpClient client = vertx.createHttpClient(clientConfig, new PoolOptions().setCleanerPeriod(50));

    // Create a connection to the server first (warm-up) before
    // we test the origin resolver
    client.request(GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end))
      .await();

    long now1 = System.currentTimeMillis();
    vertx.setPeriodic(1, id -> {
      if (System.currentTimeMillis() - now1 > 500) {
        vertx.cancelTimer(id);
      }
      client.request(GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
        .compose(request -> request
          .send()
          .expecting(HttpResponseExpectation.SC_OK)
          .compose(HttpClientResponse::end));
    });
    EndpointResolverInternal originResolver = ((HttpClientInternal) client).originResolver();
    assertWaitUntil(() -> originResolver.size() == 1);
    long now2 = System.currentTimeMillis();
    assertWaitUntil(() -> originResolver.size() == 0);
    long delta = System.currentTimeMillis() - now2;
    assertTrue(delta >= 500);
    assertTrue(delta <= 1000);
  }

  @Test
  public void testMaxKeepAlive() throws Exception {

    AtomicReference<String> ip = new AtomicReference<>("127.0.0.1");
    MockDnsServer dnsServer = new MockDnsServer();
    dnsServer.store(question -> {
      List<io.netty.handler.codec.dns.DnsRecord> responses = new ArrayList<>();
      responses.add(MockDnsServer.a("vertx.io", 0, ip.get()));
      return responses;
    });
    dnsServer.start();

    Vertx vertx = Vertx.vertx(new VertxOptions()
      .setAddressResolverOptions(new AddressResolverOptions()
        .setServers(List.of("127.0.0.1:" + MockDnsServer.PORT))));

    try {
      HttpServer server = vertx
        .createHttpServer()
        .requestHandler(request -> request.response().end())
        .listen(DEFAULT_HTTP_PORT, "127.0.0.1")
        .await();

      HttpClientConfig clientConfig = new HttpClientConfig().setResolverConfig(new ClientResolverConfig().setMaxKeepAlive(Duration.ofMillis(500)));
      HttpClient client = vertx.createHttpClient(clientConfig, new PoolOptions().setCleanerPeriod(10));
      long now = System.currentTimeMillis();
      while (true) {
        assertTrue((System.currentTimeMillis() - now) < 20_000);
        try {
          client
            .request(GET, DEFAULT_HTTP_PORT, "vertx.io", "/")
            .compose(r -> {
              return r.send().compose(HttpClientResponse::end);
            })
            .await();
        } catch (Exception e) {
          assertTrue(e instanceof ConnectException);
          assertTrue((System.currentTimeMillis() - now) >= 500);
          assertTrue((System.currentTimeMillis() - now) < 1000);
          break;
        }
        ip.set("127.0.0.2");
      }
    } finally {
      vertx.close().await();
      dnsServer.stop();
    }
  }
}
