/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.dns;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.vertx.core.VertxException;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.AddressResolver;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakedns.FakeDNSServer;
import org.junit.Test;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HostnameResolutionTest extends VertxTestBase {

  private FakeDNSServer dnsServer;
  private InetSocketAddress dnsServerAddress;

  @Override
  public void setUp() throws Exception {
    dnsServer = new FakeDNSServer().testResolveASameServer("127.0.0.1");
    dnsServer.start();
    dnsServerAddress = dnsServer.localAddress();
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    dnsServer.stop();
    super.tearDown();
  }

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.getAddressResolverOptions().addServer(dnsServerAddress.getAddress().getHostAddress() + ":" + dnsServerAddress.getPort());
    options.getAddressResolverOptions().setOptResourceEnabled(false);
    return options;
  }

  @Test
  public void testAsyncResolve() throws Exception {
    ((VertxImpl) vertx).resolveAddress("vertx.io", onSuccess(resolved -> {
      assertEquals("127.0.0.1", resolved.getHostAddress());
      testComplete();
    }));
    await();
  }

  @Test
  public void testAsyncResolveFail() throws Exception {
    ((VertxImpl) vertx).resolveAddress("vertx.com", onFailure(failure -> {
      assertTrue("Was expecting " + failure + " to be an instanceof UnknownHostException", failure instanceof UnknownHostException);
      testComplete();
    }));
    await();
  }

  @Test
  public void testNet() throws Exception {
    testNet("vertx.io");
  }

  private void testNet(String hostname) throws Exception {
    NetClient client = vertx.createNetClient();
    NetServer server = vertx.createNetServer().connectHandler(so -> {
      so.handler(buff -> {
        so.write(buff);
        so.close();
      });
    });
    try {
      CountDownLatch listenLatch = new CountDownLatch(1);
      server.listen(1234, hostname, onSuccess(s -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.connect(1234, hostname, onSuccess(so -> {
        Buffer buffer = Buffer.buffer();
        so.handler(buffer::appendBuffer);
        so.closeHandler(v -> {
          assertEquals(Buffer.buffer("foo"), buffer);
          testComplete();
        });
        so.write(Buffer.buffer("foo"));
      }));
      await();
    } finally {
      client.close();
      server.close();
    }
  }

  @Test
  public void testHttp() throws Exception {
    HttpClient client = vertx.createHttpClient();
    HttpServer server = vertx.createHttpServer().requestHandler(req -> {
      req.response().end("foo");
    });
    try {
      CountDownLatch listenLatch = new CountDownLatch(1);
      server.listen(8080, "vertx.io", onSuccess(s -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.getNow(8080, "vertx.io", "/somepath", resp -> {
        Buffer buffer = Buffer.buffer();
        resp.handler(buffer::appendBuffer);
        resp.endHandler(v -> {
          assertEquals(Buffer.buffer("foo"), buffer);
          testComplete();
        });
      });
      await();
    } finally {
      client.close();
      server.close();
    }
  }

  @Test
  public void testOptions() {
    AddressResolverOptions options = new AddressResolverOptions();
    assertEquals(AddressResolverOptions.DEFAULT_OPT_RESOURCE_ENABLED, options.isOptResourceEnabled());
    assertEquals(AddressResolverOptions.DEFAULT_SERVERS, options.getServers());
    assertEquals(AddressResolverOptions.DEFAULT_CACHE_MIN_TIME_TO_LIVE, options.getCacheMinTimeToLive());
    assertEquals(AddressResolverOptions.DEFAULT_CACHE_MAX_TIME_TO_LIVE, options.getCacheMaxTimeToLive());
    assertEquals(AddressResolverOptions.DEFAULT_CACHE_NEGATIVE_TIME_TO_LIVE, options.getCacheNegativeTimeToLive());
    assertEquals(AddressResolverOptions.DEFAULT_QUERY_TIMEOUT, options.getQueryTimeout());
    assertEquals(AddressResolverOptions.DEFAULT_MAX_QUERIES, options.getMaxQueries());
    assertEquals(AddressResolverOptions.DEFAULT_RD_FLAG, options.getRdFlag());
    assertEquals(AddressResolverOptions.DEFAULT_NDOTS, options.getNdots());
    assertEquals(AddressResolverOptions.DEFAULT_SEACH_DOMAINS, options.getSearchDomains());

    boolean optResourceEnabled = TestUtils.randomBoolean();
    List<String> servers = Arrays.asList("1.2.3.4", "5.6.7.8");
    int minTTL = TestUtils.randomPositiveInt();
    int maxTTL = minTTL + 1000;
    int negativeTTL = TestUtils.randomPositiveInt();
    int queryTimeout = 1 + TestUtils.randomPositiveInt();
    int maxQueries = 1 + TestUtils.randomPositiveInt();
    boolean rdFlag = TestUtils.randomBoolean();
    int ndots = TestUtils.randomPositiveInt() - 2;
    List<String> searchDomains = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      searchDomains.add(TestUtils.randomAlphaString(15));
    }

    assertSame(options, options.setOptResourceEnabled(optResourceEnabled));
    assertSame(options, options.setServers(new ArrayList<>(servers)));
    assertSame(options, options.setCacheMinTimeToLive(0));
    assertSame(options, options.setCacheMinTimeToLive(minTTL));
    try {
      options.setCacheMinTimeToLive(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertSame(options, options.setCacheMaxTimeToLive(0));
    assertSame(options, options.setCacheMaxTimeToLive(maxTTL));
    try {
      options.setCacheMaxTimeToLive(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertSame(options, options.setCacheNegativeTimeToLive(0));
    assertSame(options, options.setCacheNegativeTimeToLive(negativeTTL));
    try {
      options.setCacheNegativeTimeToLive(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertSame(options, options.setQueryTimeout(queryTimeout));
    try {
      options.setQueryTimeout(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertSame(options, options.setMaxQueries(maxQueries));
    try {
      options.setMaxQueries(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertSame(options, options.setRdFlag(rdFlag));
    assertSame(options, options.setSearchDomains(searchDomains));
    assertSame(options, options.setNdots(ndots));
    try {
      options.setNdots(-2);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertEquals(optResourceEnabled, options.isOptResourceEnabled());
    assertEquals(servers, options.getServers());
    assertEquals(minTTL, options.getCacheMinTimeToLive());
    assertEquals(maxTTL, options.getCacheMaxTimeToLive());
    assertEquals(negativeTTL, options.getCacheNegativeTimeToLive());
    assertEquals(queryTimeout, options.getQueryTimeout());
    assertEquals(maxQueries, options.getMaxQueries());
    assertEquals(rdFlag, options.getRdFlag());
    assertEquals(ndots, options.getNdots());
    assertEquals(searchDomains, options.getSearchDomains());

    // Test copy and json copy
    AddressResolverOptions copy = new AddressResolverOptions(options);
    AddressResolverOptions jsonCopy = new AddressResolverOptions(options.toJson());

    options.setOptResourceEnabled(AddressResolverOptions.DEFAULT_OPT_RESOURCE_ENABLED);
    options.getServers().clear();
    options.setCacheMinTimeToLive(AddressResolverOptions.DEFAULT_CACHE_MIN_TIME_TO_LIVE);
    options.setCacheMaxTimeToLive(AddressResolverOptions.DEFAULT_CACHE_MAX_TIME_TO_LIVE);
    options.setCacheNegativeTimeToLive(AddressResolverOptions.DEFAULT_CACHE_NEGATIVE_TIME_TO_LIVE);
    options.setQueryTimeout(AddressResolverOptions.DEFAULT_QUERY_TIMEOUT);
    options.setMaxQueries(AddressResolverOptions.DEFAULT_MAX_QUERIES);
    options.setRdFlag(AddressResolverOptions.DEFAULT_RD_FLAG);
    options.setNdots(AddressResolverOptions.DEFAULT_NDOTS);
    options.setSearchDomains(AddressResolverOptions.DEFAULT_SEACH_DOMAINS);

    assertEquals(optResourceEnabled, copy.isOptResourceEnabled());
    assertEquals(servers, copy.getServers());
    assertEquals(minTTL, copy.getCacheMinTimeToLive());
    assertEquals(maxTTL, copy.getCacheMaxTimeToLive());
    assertEquals(negativeTTL, copy.getCacheNegativeTimeToLive());
    assertEquals(queryTimeout, copy.getQueryTimeout());
    assertEquals(maxQueries, copy.getMaxQueries());
    assertEquals(rdFlag, copy.getRdFlag());
    assertEquals(ndots, copy.getNdots());
    assertEquals(searchDomains, copy.getSearchDomains());

    assertEquals(optResourceEnabled, jsonCopy.isOptResourceEnabled());
    assertEquals(servers, jsonCopy.getServers());
    assertEquals(minTTL, jsonCopy.getCacheMinTimeToLive());
    assertEquals(maxTTL, jsonCopy.getCacheMaxTimeToLive());
    assertEquals(negativeTTL, jsonCopy.getCacheNegativeTimeToLive());
    assertEquals(queryTimeout, jsonCopy.getQueryTimeout());
    assertEquals(maxQueries, jsonCopy.getMaxQueries());
    assertEquals(rdFlag, jsonCopy.getRdFlag());
    assertEquals(ndots, jsonCopy.getNdots());
    assertEquals(searchDomains, jsonCopy.getSearchDomains());
  }

  @Test
  public void testDefaultJsonOptions() {
    AddressResolverOptions options = new AddressResolverOptions(new JsonObject());
    assertEquals(AddressResolverOptions.DEFAULT_OPT_RESOURCE_ENABLED, options.isOptResourceEnabled());
    assertEquals(AddressResolverOptions.DEFAULT_SERVERS, options.getServers());
    assertEquals(AddressResolverOptions.DEFAULT_CACHE_MIN_TIME_TO_LIVE, options.getCacheMinTimeToLive());
    assertEquals(AddressResolverOptions.DEFAULT_CACHE_MAX_TIME_TO_LIVE, options.getCacheMaxTimeToLive());
    assertEquals(AddressResolverOptions.DEFAULT_CACHE_NEGATIVE_TIME_TO_LIVE, options.getCacheNegativeTimeToLive());
    assertEquals(AddressResolverOptions.DEFAULT_QUERY_TIMEOUT, options.getQueryTimeout());
    assertEquals(AddressResolverOptions.DEFAULT_MAX_QUERIES, options.getMaxQueries());
    assertEquals(AddressResolverOptions.DEFAULT_RD_FLAG, options.getRdFlag());
    assertEquals(AddressResolverOptions.DEFAULT_SEACH_DOMAINS, options.getSearchDomains());
    assertEquals(AddressResolverOptions.DEFAULT_NDOTS, options.getNdots());
  }

  @Test
  public void testAsyncResolveConnectIsNotifiedOnChannelEventLoop() throws Exception {
    CountDownLatch listenLatch = new CountDownLatch(1);
    NetServer server = vertx.createNetServer().connectHandler(so -> {
    });
    try {
      server.listen(1234, "localhost", onSuccess(v -> listenLatch.countDown()));
      awaitLatch(listenLatch);
      AtomicReference<Thread> channelThread = new AtomicReference<>();
      CountDownLatch connectLatch = new CountDownLatch(1);
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.channelFactory(((VertxInternal)vertx).transport().channelFactory(false));
      bootstrap.group(vertx.nettyEventLoopGroup());
      bootstrap.resolver(((VertxInternal) vertx).nettyAddressResolverGroup());
      bootstrap.handler(new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel ch) throws Exception {
          channelThread.set(Thread.currentThread());
          connectLatch.countDown();
        }
      });
      ChannelFuture channelFut = bootstrap.connect("localhost", 1234);
      awaitLatch(connectLatch);
      channelFut.addListener(v -> {
        assertTrue(v.isSuccess());
        assertEquals(channelThread.get(), Thread.currentThread());
        testComplete();
      });
      await();
    } finally {
      server.close();
    }
  }

  @Test
  public void testInvalidHostsConfig() {
    try {
      AddressResolverOptions options = new AddressResolverOptions().setHostsPath("whatever.txt");
      vertx(new VertxOptions().setAddressResolverOptions(options));
      fail();
    } catch (VertxException ignore) {
    }
  }

  @Test
  public void testResolveFromClasspath() {
    VertxInternal vertx = (VertxInternal) vertx(new VertxOptions().setAddressResolverOptions(new AddressResolverOptions().setHostsPath("hosts_config.txt")));
    vertx.resolveAddress("server.net", onSuccess(addr -> {
      assertEquals("192.168.0.15", addr.getHostAddress());
      assertEquals("server.net", addr.getHostName());
      testComplete();
    }));
    await();
  }

  @Test
  public void testResolveFromFile() {
    File f = new File(new File(new File(new File("src"), "test"), "resources"), "hosts_config.txt");
    VertxInternal vertx = (VertxInternal) vertx(new VertxOptions().setAddressResolverOptions(new AddressResolverOptions().setHostsPath(f.getAbsolutePath())));
    vertx.resolveAddress("server.net", onSuccess(addr -> {
      assertEquals("192.168.0.15", addr.getHostAddress());
      assertEquals("server.net", addr.getHostName());
      testComplete();
    }));
    await();
  }

  @Test
  public void testResolveFromBuffer() {
    VertxInternal vertx = (VertxInternal) vertx(new VertxOptions().setAddressResolverOptions(new AddressResolverOptions().setHostsValue(Buffer.buffer("192.168.0.15 server.net"))));
    vertx.resolveAddress("server.net", onSuccess(addr -> {
      assertEquals("192.168.0.15", addr.getHostAddress());
      assertEquals("server.net", addr.getHostName());
      testComplete();
    }));
    await();
  }

  @Test
  public void testCaseInsensitiveResolveFromHosts() {
    VertxInternal vertx = (VertxInternal) vertx(new VertxOptions().setAddressResolverOptions(new AddressResolverOptions().setHostsPath("hosts_config.txt")));
    vertx.resolveAddress("SERVER.NET", onSuccess(addr -> {
      assertEquals("192.168.0.15", addr.getHostAddress());
      assertEquals("server.net", addr.getHostName());
      testComplete();
    }));
    await();
  }

  @Test
  public void testTrailingDotResolveFromHosts() {
    VertxInternal vertx = (VertxInternal) vertx(new VertxOptions().setAddressResolverOptions(new AddressResolverOptions().setHostsPath("hosts_config.txt")));
    vertx.resolveAddress("server.net.", onSuccess(addr -> {
      assertEquals("192.168.0.15", addr.getHostAddress());
      assertEquals("server.net", addr.getHostName());
      testComplete();
    }));
    await();
  }

  @Test
  public void testResolveMissingLocalhost() throws Exception {

    InetAddress localhost = InetAddress.getByName("localhost");

    // Set a dns resolver that won't resolve localhost
    dnsServer.testResolveASameServer("127.0.0.1");

    // Test using the resolver API
    VertxInternal vertx = (VertxInternal) vertx(new VertxOptions().setAddressResolverOptions(
        new AddressResolverOptions().
            addServer(dnsServerAddress.getAddress().getHostAddress() + ":" + dnsServerAddress.getPort()).
            setOptResourceEnabled(false)
    ));
    CompletableFuture<Void> test1 = new CompletableFuture<>();
    vertx.resolveAddress("localhost", ar -> {
      if (ar.succeeded()) {
        InetAddress resolved = ar.result();
        if (resolved.equals(localhost)) {
          test1.complete(null);
        } else {
          test1.completeExceptionally(new AssertionError("Unexpected localhost value " + resolved));
        }
      } else {
        test1.completeExceptionally(ar.cause());
      }
    });
    test1.get(10, TimeUnit.SECONDS);

    CompletableFuture<Void> test2 = new CompletableFuture<>();
    vertx.resolveAddress("LOCALHOST", ar -> {
      if (ar.succeeded()) {
        InetAddress resolved = ar.result();
        if (resolved.equals(localhost)) {
          test2.complete(null);
        } else {
          test2.completeExceptionally(new AssertionError("Unexpected localhost value " + resolved));
        }
      } else {
        test2.completeExceptionally(ar.cause());
      }
    });
    test2.get(10, TimeUnit.SECONDS);

    // Test using bootstrap
    CompletableFuture<Void> test3 = new CompletableFuture<>();
    NetServer server = vertx.createNetServer(new NetServerOptions().setPort(1234).setHost(localhost.getHostAddress()));
    try {
      server.connectHandler(so -> {
        so.write("hello").end();
      });
      server.listen(ar -> {
        if (ar.succeeded()) {
          test3.complete(null);
        } else {
          test3.completeExceptionally(ar.cause());
        }
      });
      test3.get(10, TimeUnit.SECONDS);

      CompletableFuture<Void> test4 = new CompletableFuture<>();
      NetClient client = vertx.createNetClient();
      client.connect(1234, "localhost", ar -> {
        if (ar.succeeded()) {
          test4.complete(null);
        } else {
          test4.completeExceptionally(ar.cause());
        }
      });
      test4.get(10, TimeUnit.SECONDS);
    } finally {
      server.close();
    }
  }

  @Test
  public void testSearchDomain() throws Exception {

    String addr_host1_foo_com = "127.0.0.1";
    String addr_host1 = "127.0.0.2";
    String addr_host3 = "127.0.0.3";
    String addr_host4_sub_foo_com = "127.0.0.4";
    String addr_host5_sub_foo_com = "127.0.0.5";
    String addr_host5_sub = "127.0.0.6";
    String addr_host6_sub_sub_foo_com = "127.0.0.7";
    String addr_host7_sub_sub_foo_com = "127.0.0.8";
    String addr_host7_sub_sub = "127.0.0.9";

    Map<String, String> records = new HashMap<>();
    records.put("host1.foo.com", addr_host1_foo_com);
    records.put("host1", addr_host1);
    records.put("host3", addr_host3);
    records.put("host4.sub.foo.com", addr_host4_sub_foo_com);
    records.put("host5.sub.foo.com", addr_host5_sub_foo_com);
    records.put("host5.sub", addr_host5_sub);
    records.put("host6.sub.sub.foo.com", addr_host6_sub_sub_foo_com);
    records.put("host7.sub.sub.foo.com", addr_host7_sub_sub_foo_com);
    records.put("host7.sub.sub", addr_host7_sub_sub);

    dnsServer.testResolveA(records);
    VertxInternal vertx = (VertxInternal) vertx(new VertxOptions().setAddressResolverOptions(
        new AddressResolverOptions().
            addServer(dnsServerAddress.getAddress().getHostAddress() + ":" + dnsServerAddress.getPort()).
            setOptResourceEnabled(false).
            setNdots(2).
            addSearchDomain("foo.com")
    ));

    // host1 resolves host1.foo.com with foo.com search domain
    CountDownLatch latch1 = new CountDownLatch(1);
    vertx.resolveAddress("host1", onSuccess(resolved -> {
      assertEquals(addr_host1_foo_com, resolved.getHostAddress());
      latch1.countDown();
    }));
    awaitLatch(latch1);

    // "host1." absolute query
    CountDownLatch latch2 = new CountDownLatch(1);
    vertx.resolveAddress("host1.", onSuccess(resolved -> {
      assertEquals(addr_host1, resolved.getHostAddress());
      latch2.countDown();
    }));
    awaitLatch(latch2);

    // "host2" not resolved
    CountDownLatch latch3 = new CountDownLatch(1);
    vertx.resolveAddress("host2", onFailure(cause -> {
      assertTrue(cause instanceof UnknownHostException);
      latch3.countDown();
    }));
    awaitLatch(latch3);

    // "host3" resolves to addr_host3 as fallback
    CountDownLatch latch4 = new CountDownLatch(1);
    vertx.resolveAddress("host3", onSuccess(cause -> {
      assertEquals(addr_host3, cause.getHostAddress());
      latch4.countDown();
    }));
    awaitLatch(latch4);

    // "host3." does not contain a dot but is absolute
    CountDownLatch latch5 = new CountDownLatch(1);
    vertx.resolveAddress("host3.", onSuccess(resolved -> {
      assertEquals(addr_host3, resolved.getHostAddress());
      latch5.countDown();
    }));
    awaitLatch(latch5);

    // "host4.sub" contains a dot but not resolved then resolved to "host4.sub.foo.com" with "foo.com" search domain
    CountDownLatch latch6 = new CountDownLatch(1);
    vertx.resolveAddress("host4.sub", onSuccess(resolved -> {
      assertEquals(addr_host4_sub_foo_com, resolved.getHostAddress());
      latch6.countDown();
    }));
    awaitLatch(latch6);

    // "host5.sub" contains one dots and is resolved via search domain to "host5.sub.foo.com" and not to "host5.sub"
    CountDownLatch latch7 = new CountDownLatch(1);
    vertx.resolveAddress("host5.sub", onSuccess(resolved -> {
      assertEquals(addr_host5_sub_foo_com, resolved.getHostAddress());
      latch7.countDown();
    }));
    awaitLatch(latch7);

    // "host6.sub.sub" contains two dots and is resolved to "host6.sub.sub.foo.com" as fallback
    CountDownLatch latch8 = new CountDownLatch(1);
    vertx.resolveAddress("host6.sub.sub", onSuccess(resolved -> {
      assertEquals(addr_host6_sub_sub_foo_com, resolved.getHostAddress());
      latch8.countDown();
    }));
    awaitLatch(latch8);

    // "host6.sub.sub" contains two dots and is resolved to "host6.sub.sub" and not to "host6.sub.sub.foo.com"
    CountDownLatch latch9 = new CountDownLatch(1);
    vertx.resolveAddress("host7.sub.sub", onSuccess(resolved -> {
      assertEquals(addr_host7_sub_sub, resolved.getHostAddress());
      latch9.countDown();
    }));
    awaitLatch(latch9);
  }

  @Test
  public void testMultipleSearchDomain() throws Exception {

    Map<String, String> records = new HashMap<>();
    records.put("host1.foo.com", "127.0.0.1");
    records.put("host2.bar.com", "127.0.0.2");
    records.put("host3.bar.com", "127.0.0.3");
    records.put("host3.foo.com", "127.0.0.4");

    dnsServer.testResolveA(records);
    VertxInternal vertx = (VertxInternal) vertx(new VertxOptions().setAddressResolverOptions(
        new AddressResolverOptions().
            setHostsValue(Buffer.buffer()).
            setNdots(1).
            addServer(dnsServerAddress.getAddress().getHostAddress() + ":" + dnsServerAddress.getPort()).
            setOptResourceEnabled(false).
            addSearchDomain("foo.com").
            addSearchDomain("bar.com")
    ));

    // "host1" resolves via the "foo.com" search path
    CountDownLatch latch1 = new CountDownLatch(1);
    vertx.resolveAddress("host1", onSuccess(resolved -> {
      assertEquals("127.0.0.1", resolved.getHostAddress());
      latch1.countDown();
    }));
    awaitLatch(latch1);

    // "host2" resolves via the "bar.com" search path
    CountDownLatch latch2 = new CountDownLatch(1);
    vertx.resolveAddress("host2", onSuccess(resolved -> {
      assertEquals("127.0.0.2", resolved.getHostAddress());
      latch2.countDown();
    }));
    awaitLatch(latch2);

    // "host3" resolves via the the "foo.com" search path as it is the first one
    CountDownLatch latch3 = new CountDownLatch(1);
    vertx.resolveAddress("host3", onSuccess(resolved -> {
      assertEquals("127.0.0.4", resolved.getHostAddress());
      latch3.countDown();
    }));
    awaitLatch(latch3);

    // "host4" does not resolve
    vertx.resolveAddress("host4", onFailure(cause -> {
      assertTrue(cause instanceof UnknownHostException);
      testComplete();
    }));

    await();
  }

  @Test
  public void testSearchDomainWithNdots2() throws Exception {

    Map<String, String> records = new HashMap<>();
    records.put("host1.sub.foo.com", "127.0.0.1");
    records.put("host2.sub.foo.com", "127.0.0.2");
    records.put("host2.sub", "127.0.0.3");

    dnsServer.testResolveA(records);
    VertxInternal vertx = (VertxInternal) vertx(new VertxOptions().setAddressResolverOptions(
        new AddressResolverOptions().
            addServer(dnsServerAddress.getAddress().getHostAddress() + ":" + dnsServerAddress.getPort()).
            setOptResourceEnabled(false).
            addSearchDomain("foo.com").
            setNdots(2)
    ));

    CountDownLatch latch1 = new CountDownLatch(1);
    vertx.resolveAddress("host1.sub", onSuccess(resolved -> {
      assertEquals("127.0.0.1", resolved.getHostAddress());
      latch1.countDown();
    }));
    awaitLatch(latch1);

    // "host2.sub" is resolved with the foo.com search domain as ndots = 2
    CountDownLatch latch2 = new CountDownLatch(1);
    vertx.resolveAddress("host2.sub", onSuccess(resolved -> {
      assertEquals("127.0.0.2", resolved.getHostAddress());
      latch2.countDown();
    }));
    awaitLatch(latch2);
  }

  @Test
  public void testSearchDomainWithNdots0() throws Exception {

    Map<String, String> records = new HashMap<>();
    records.put("host1", "127.0.0.2");
    records.put("host1.foo.com", "127.0.0.3");

    dnsServer.testResolveA(records);
    VertxInternal vertx = (VertxInternal) vertx(new VertxOptions().setAddressResolverOptions(
        new AddressResolverOptions().
            addServer(dnsServerAddress.getAddress().getHostAddress() + ":" + dnsServerAddress.getPort()).
            setOptResourceEnabled(false).
            addSearchDomain("foo.com").
            setNdots(0)
    ));

    // "host1" resolves directly as ndots = 0
    CountDownLatch latch1 = new CountDownLatch(1);
    vertx.resolveAddress("host1", onSuccess(resolved -> {
      assertEquals("127.0.0.2", resolved.getHostAddress());
      latch1.countDown();
    }));
    awaitLatch(latch1);

    // "host1.foo.com" resolves to host1.foo.com
    CountDownLatch latch2 = new CountDownLatch(1);
    vertx.resolveAddress("host1.foo.com", onSuccess(resolved -> {
      assertEquals("127.0.0.3", resolved.getHostAddress());
      latch2.countDown();
    }));
    awaitLatch(latch2);
  }

  @Test
  public void testNetSearchDomain() throws Exception {
    Map<String, String> records = new HashMap<>();
    records.put("host1.foo.com", "127.0.0.1");
    dnsServer.testResolveA(records);
    vertx.close();
    vertx = vertx(new VertxOptions().setAddressResolverOptions(
        new AddressResolverOptions().
            setHostsValue(Buffer.buffer()).
            setNdots(1).
            addServer(dnsServerAddress.getAddress().getHostAddress() + ":" + dnsServerAddress.getPort()).
            setOptResourceEnabled(false).
            addSearchDomain("foo.com")
    ));
    testNet("host1");
  }

  @Test
  public void testParseResolvConf() {
    assertEquals(-1, AddressResolver.parseNdotsOptionFromResolvConf("options"));
    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("options ndots: 4"));
    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("\noptions ndots: 4"));
    assertEquals(-1, AddressResolver.parseNdotsOptionFromResolvConf("boptions ndots: 4"));
    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf(" options ndots: 4"));
    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("\toptions ndots: 4"));
    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("\foptions ndots: 4"));
    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("\n options ndots: 4"));

    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("options\tndots: 4"));
    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("options\fndots: 4"));
    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("options  ndots: 4"));
    assertEquals(-1, AddressResolver.parseNdotsOptionFromResolvConf("options\nndots: 4"));

    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("options ndots:4"));
    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("options ndots:\t4"));
    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("options ndots:  4"));
    assertEquals(-1, AddressResolver.parseNdotsOptionFromResolvConf("options ndots:\n4"));

    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("options ndots:4 "));
    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("options ndots:4\t"));
    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("options ndots:4\f"));
    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("options ndots:4\n"));
    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("options ndots:4\r"));
    assertEquals(-1, AddressResolver.parseNdotsOptionFromResolvConf("options ndots:4_"));

    assertEquals(2, AddressResolver.parseNdotsOptionFromResolvConf("options ndots:4\noptions ndots:2"));
    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("options ndots:4 debug"));
    assertEquals(4, AddressResolver.parseNdotsOptionFromResolvConf("options debug ndots:4"));

    assertEquals(false, AddressResolver.parseRotateOptionFromResolvConf("options"));
    assertEquals(true, AddressResolver.parseRotateOptionFromResolvConf("options rotate"));
    assertEquals(true, AddressResolver.parseRotateOptionFromResolvConf("options rotate\n"));
    assertEquals(false, AddressResolver.parseRotateOptionFromResolvConf("options\nrotate"));
  }

  @Test
  public void testResolveLocalhost() {
    AddressResolver resolver = new AddressResolver((VertxImpl) vertx, new AddressResolverOptions());

    resolver.resolveHostname("LOCALHOST", res -> {
      if (res.succeeded()) {
        assertEquals("localhost", res.result().getHostName().toLowerCase(Locale.ENGLISH));
        resolver.resolveHostname("LocalHost", res2 -> {
          if (res2.succeeded()) {
            assertEquals("localhost", res2.result().getHostName().toLowerCase(Locale.ENGLISH));
            resolver.resolveHostname("localhost", res3 -> {
              if (res3.succeeded()) {
                assertEquals("localhost", res3.result().getHostName().toLowerCase(Locale.ENGLISH));
                testComplete();
              } else {
                fail(res3.cause());
              }
            });
          } else {
            fail(res2.cause());
          }
        });
      } else {
        fail(res.cause());
      }
    });

    await();
  }

  @Test
  public void testRotationalServerSelection() throws Exception {
    testServerSelection(true, false);
  }

  @Test
  public void testRotationalServerSelectionWithCache() throws Exception {
    testServerSelection(true, true);
  }

  @Test
  public void testFirstServerSelection() throws Exception {
    testServerSelection(false, false);
  }

  private void testServerSelection(boolean rotateServers, boolean cache) throws Exception {
    int num = VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE + 4;
    List<FakeDNSServer> dnsServers = new ArrayList<>();
    try {
      for (int index = 1; index <= num; index++) {
        FakeDNSServer server = new FakeDNSServer().store(FakeDNSServer.A_store(Collections.singletonMap("vertx.io", "127.0.0." + index)));
        server.port(FakeDNSServer.PORT + index);
        server.start();
        dnsServers.add(server);
      }
      AddressResolverOptions options = new AddressResolverOptions();
      options.setRotateServers(rotateServers);
      options.setOptResourceEnabled(false);
      if (!cache) {
        options.setCacheMaxTimeToLive(0);
      }
      for (int i = 0; i < num; i++) {
        InetSocketAddress dnsServerAddress = dnsServers.get(i).localAddress();
        options.addServer(dnsServerAddress.getAddress().getHostAddress() + ":" + dnsServerAddress.getPort());
      }
      AddressResolver resolver = new AddressResolver(vertx, options);
      for (int i = 0; i < num; i++) {
        CompletableFuture<InetAddress> result = new CompletableFuture<>();
        resolver.resolveHostname("vertx.io", ar -> {
          if (ar.succeeded()) {
            result.complete(ar.result());
          } else {
            result.completeExceptionally(ar.cause());
          }
        });
        String resolved = result.get(10, TimeUnit.SECONDS).getHostAddress();
        int expected;
        if (rotateServers && !cache) {
          expected = 1 + i;
        } else {
          expected = 1;
        }
        assertEquals("127.0.0." + expected, resolved);
      }
    } finally {
      dnsServers.forEach(FakeDNSServer::stop);
    }
  }


  @Test
  public void testServerFailover() throws Exception {
    FakeDNSServer server = new FakeDNSServer().store(FakeDNSServer.A_store(Collections.singletonMap("vertx.io", "127.0.0.1"))).port(FakeDNSServer.PORT + 2);
    try {
      AddressResolverOptions options = new AddressResolverOptions();
      options.setOptResourceEnabled(false);
      options.setMaxQueries(4); // 2 + 2
      server.start();
      InetSocketAddress dnsServerAddress = server.localAddress();
      // First server is unreachable
      options.addServer(dnsServerAddress.getAddress().getHostAddress() + ":" + (FakeDNSServer.PORT + 1));
      // Second server is the failed over server
      options.addServer(dnsServerAddress.getAddress().getHostAddress() + ":" + dnsServerAddress.getPort());
      AddressResolver resolver = new AddressResolver((VertxImpl) vertx, options);
      CompletableFuture<InetAddress> result = new CompletableFuture<>();
      resolver.resolveHostname("vertx.io", ar -> {
        if (ar.succeeded()) {
          result.complete(ar.result());
        } else {
          result.completeExceptionally(ar.cause());
        }
      });
      String resolved = result.get(10, TimeUnit.SECONDS).getHostAddress();
      assertEquals("127.0.0.1", resolved);
    } finally {
      server.stop();
    }
  }
}
