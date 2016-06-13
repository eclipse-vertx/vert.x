/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.Utils;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.test.fakedns.FakeDNSServer;
import org.junit.Test;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HostnameResolutionTest extends VertxTestBase {

  private FakeDNSServer dnsServer;
  private InetSocketAddress dnsServerAddress;

  @Override
  public void setUp() throws Exception {
    dnsServer = FakeDNSServer.testResolveASameServer("127.0.0.1");
    dnsServer.start();
    dnsServerAddress = (InetSocketAddress) dnsServer.getTransports()[0].getAcceptor().getLocalAddress();
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    if (dnsServer.isStarted()) {
      dnsServer.stop();
    }
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
    ((VertxImpl)vertx).resolveAddress("vertx.io", onSuccess(resolved -> {
      assertEquals("127.0.0.1", resolved.getHostAddress());
      testComplete();
    }));
    await();
  }

  @Test
  public void testAsyncResolveFail() throws Exception {
    ((VertxImpl)vertx).resolveAddress("vertx.com", onFailure(failure -> {
      assertEquals(UnknownHostException.class, failure.getClass());
      testComplete();
    }));
    await();
  }

  @Test
  public void testNet() throws Exception {
    NetClient client = vertx.createNetClient();
    NetServer server = vertx.createNetServer().connectHandler(so -> {
      so.handler(buff -> {
        so.write(buff);
        so.close();
      });
    });
    try {
      CountDownLatch listenLatch = new CountDownLatch(1);
      server.listen(1234, "vertx.io", onSuccess(s -> {
        listenLatch.countDown();
      }));
      awaitLatch(listenLatch);
      client.connect(1234, "vertx.io", onSuccess(so -> {
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

    boolean optResourceEnabled = TestUtils.randomBoolean();
    List<String> servers = Arrays.asList("1.2.3.4", "5.6.7.8");
    int minTTL = TestUtils.randomPositiveInt();
    int maxTTL = minTTL + TestUtils.randomPositiveInt();
    int negativeTTL = TestUtils.randomPositiveInt();
    int queryTimeout = 1 + TestUtils.randomPositiveInt();
    int maxQueries = 1 + TestUtils.randomPositiveInt();
    boolean rdFlag = TestUtils.randomBoolean();

    assertSame(options, options.setOptResourceEnabled(optResourceEnabled));
    assertSame(options, options.setServers(new ArrayList<>(servers)));
    assertSame(options, options.setCacheMinTimeToLive(minTTL));
    assertSame(options, options.setCacheMaxTimeToLive(maxTTL));
    assertSame(options, options.setCacheNegativeTimeToLive(negativeTTL));
    assertSame(options, options.setQueryTimeout(queryTimeout));
    assertSame(options, options.setMaxQueries(maxQueries));
    assertSame(options, options.setRdFlag(rdFlag));

    assertEquals(optResourceEnabled, options.isOptResourceEnabled());
    assertEquals(servers, options.getServers());
    assertEquals(minTTL, options.getCacheMinTimeToLive());
    assertEquals(maxTTL, options.getCacheMaxTimeToLive());
    assertEquals(negativeTTL, options.getCacheNegativeTimeToLive());
    assertEquals(queryTimeout, options.getQueryTimeout());
    assertEquals(maxQueries, options.getMaxQueries());
    assertEquals(rdFlag, options.getRdFlag());

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

    assertEquals(optResourceEnabled, copy.isOptResourceEnabled());
    assertEquals(servers, copy.getServers());
    assertEquals(minTTL, copy.getCacheMinTimeToLive());
    assertEquals(maxTTL, copy.getCacheMaxTimeToLive());
    assertEquals(negativeTTL, copy.getCacheNegativeTimeToLive());
    assertEquals(queryTimeout, copy.getQueryTimeout());
    assertEquals(maxQueries, copy.getMaxQueries());
    assertEquals(rdFlag, copy.getRdFlag());

    assertEquals(optResourceEnabled, jsonCopy.isOptResourceEnabled());
    assertEquals(servers, jsonCopy.getServers());
    assertEquals(minTTL, jsonCopy.getCacheMinTimeToLive());
    assertEquals(maxTTL, jsonCopy.getCacheMaxTimeToLive());
    assertEquals(negativeTTL, jsonCopy.getCacheNegativeTimeToLive());
    assertEquals(queryTimeout, jsonCopy.getQueryTimeout());
    assertEquals(maxQueries, jsonCopy.getMaxQueries());
    assertEquals(rdFlag, jsonCopy.getRdFlag());
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
  }

  @Test
  public void testAsyncResolveConnectIsNotifiedOnChannelEventLoop() throws Exception {
    CountDownLatch listenLatch = new CountDownLatch(1);
    NetServer s = vertx.createNetServer().connectHandler(so -> {});
    s.listen(1234, "localhost", onSuccess(v -> listenLatch.countDown()));
    awaitLatch(listenLatch);
    AtomicReference<Thread> channelThread = new AtomicReference<>();
    CountDownLatch connectLatch = new CountDownLatch(1);
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.channel(NioSocketChannel.class);
    bootstrap.group(vertx.nettyEventLoopGroup());
    bootstrap.resolver(((VertxInternal)vertx).addressResolver().nettyAddressResolverGroup());
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
  }

  @Test
  public void testInvalidHostsConfig() {
    try {
      AddressResolverOptions options = new AddressResolverOptions().setHostsPath("whatever.txt");
      Vertx.vertx(new VertxOptions().setAddressResolverOptions(options));
      fail();
    } catch (VertxException ignore) {
    }
  }

  @Test
  public void testResolveFromClasspath() {
    VertxInternal vertx = (VertxInternal) Vertx.vertx(new VertxOptions().setAddressResolverOptions(new AddressResolverOptions().setHostsPath("hosts_config.txt")));
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
    VertxInternal vertx = (VertxInternal) Vertx.vertx(new VertxOptions().setAddressResolverOptions(new AddressResolverOptions().setHostsPath(f.getAbsolutePath())));
    vertx.resolveAddress("server.net", onSuccess(addr -> {
      assertEquals("192.168.0.15", addr.getHostAddress());
      assertEquals("server.net", addr.getHostName());
      testComplete();
    }));
    await();
  }

  @Test
  public void testResolveFromBuffer() {
    VertxInternal vertx = (VertxInternal) Vertx.vertx(new VertxOptions().setAddressResolverOptions(new AddressResolverOptions().setHostsValue(Buffer.buffer("192.168.0.15 server.net"))));
    vertx.resolveAddress("server.net", onSuccess(addr -> {
      assertEquals("192.168.0.15", addr.getHostAddress());
      assertEquals("server.net", addr.getHostName());
      testComplete();
    }));
    await();
  }

/*
  @Test
  public void testResolveLocalhostOnWindows() {
    String old = System.getProperty("os.name");
    VertxInternal vertx = (VertxInternal) Vertx.vertx(new VertxOptions().setAddressResolverOptions(new AddressResolverOptions().setHostsValue(Buffer.buffer())));
    try {
      System.setProperty("os.name", "Windows 10");
      assertTrue(Utils.isWindows());
      vertx.resolveAddress("localhost", onSuccess(addr -> {
        assertEquals("127.0.0.1", addr.getHostAddress());
        testComplete();
      }));
    } finally {
      System.setProperty("os.name", old);
    }
  }
*/
}
