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

package io.vertx.tests.vertx;

import io.netty.channel.EventLoopGroup;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.transports.NioTransport;
import io.vertx.core.internal.VertxBootstrap;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.transport.Transport;
import io.vertx.core.spi.cluster.NodeListener;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import io.vertx.test.fakedns.FakeDNSServer;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Thomas Segismont
 */
public class VertxStartFailureTest extends AsyncTestBase {

  @Test
  public void testEventBusStartFailure() throws Exception {
    FakeDNSServer dnsServer = new FakeDNSServer().testResolveASameServer("127.0.0.1");
    dnsServer.start();
    try {
      InetSocketAddress dnsServerAddress = dnsServer.localAddress();

      // will trigger java.net.UnknownHostException
      String hostName = "zoom.zoom.zen.tld";
      FakeClusterManager clusterManager = new FakeClusterManager();
      VertxOptions options = new VertxOptions();
      options.getAddressResolverOptions().addServer(dnsServerAddress.getAddress().getHostAddress() + ":" + dnsServerAddress.getPort());
      options.getEventBusOptions().setHost(hostName);
      Throwable failure = failStart(options, clusterManager);
      assertTrue("Was expecting failure to be an instance of UnknownHostException", failure instanceof UnknownHostException);
    } finally {
      dnsServer.stop();
    }
  }

  @Test
  public void testClusterManagerStartFailure() throws Exception {
    Exception expected = new Exception();
    FakeClusterManager clusterManager = new FakeClusterManager() {
      @Override
      public void join(Promise<Void> promise) {
        promise.fail(expected);
      }
    };
    VertxOptions options = new VertxOptions();
    Throwable failure = failStart(options, clusterManager);
    assertSame(expected,  failure);
  }

  @Test
  public void testHAManagerGetMapFailure() throws Exception {
    RuntimeException expected = new RuntimeException();
    FakeClusterManager clusterManager = new FakeClusterManager() {
      @Override
      public <K, V> Map<K, V> getSyncMap(String name) {
        // Triggers failure when Vertx wants to get __vertx.haInfo during start
        throw expected;
      }
    };
    VertxOptions options = new VertxOptions().setHAEnabled(true);
    Throwable failure = failStart(options, clusterManager);
    assertSame(expected,  failure);
  }

  @Test
  public void testHAManagerInitFailure() throws Exception {
    RuntimeException expected = new RuntimeException();
    FakeClusterManager clusterManager = new FakeClusterManager() {
      @Override
      public void nodeListener(NodeListener listener) {
        // Triggers HAManager init failure
        throw expected;
      }
    };
    VertxOptions options = new VertxOptions().setHAEnabled(true);
    Throwable failure = failStart(options, clusterManager);
    assertSame(expected,  failure);
  }

  private Throwable failStart(VertxOptions options, ClusterManager clusterManager) throws Exception {
    List<EventLoopGroup> loops = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);
    Transport transport = new NioTransport() {
      @Override
      public EventLoopGroup eventLoopGroup(int type, int nThreads, ThreadFactory threadFactory, int ioRatio) {
        EventLoopGroup eventLoop = super.eventLoopGroup(type, nThreads, threadFactory, ioRatio);
        loops.add(eventLoop);
        return eventLoop;
      }
    };
    AtomicReference<AsyncResult<Vertx>> resultRef = new AtomicReference<>();
    VertxBootstrap.create().options(options).clusterManager(clusterManager).init().transport(transport).clusteredVertx().onComplete(ar -> {
      resultRef.set(ar);
      latch.countDown();
    });
    awaitLatch(latch);
    assertFalse(resultRef.get() == null);
    assertTrue(resultRef.get().failed());
    loops.forEach(loop -> {
      waitUntil(loop::isShutdown);
    });
    return resultRef.get().cause();
  }
}
