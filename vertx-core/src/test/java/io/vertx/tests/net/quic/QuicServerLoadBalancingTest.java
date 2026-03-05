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
package io.vertx.tests.net.quic;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.quic.QuicServerImpl;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.vertx.tests.net.quic.QuicClientTest.clientOptions;

public class QuicServerLoadBalancingTest extends VertxTestBase {

  private List<QuicServer> servers;
  private QuicClient client;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    servers = new ArrayList<>();
    client = vertx.createQuicClient(clientOptions(), QuicClientTest.SSL_OPTIONS);
  }

  @Override
  protected void tearDown() throws Exception {
    client.close().await();
    servers.forEach(server -> {
      server.close().await();
    });
    super.tearDown();
  }

  private QuicServer server() {
    QuicServer server = vertx.createQuicServer(new QuicServerConfig().setLoadBalanced(true), QuicServerTest.SSL_OPTIONS);
    servers.add(server);
    return server;
  }

  @Test
  public void testSingleServer() {
    AtomicInteger inflight = new AtomicInteger();
    QuicServer server = server();
    server.handler(conn -> {
      inflight.getAndIncrement();
      conn.closeHandler(v -> {
        inflight.getAndDecrement();
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    assertWaitUntil(() -> inflight.get() == 1);
    connection.close().await();
    assertWaitUntil(() -> inflight.get() == 0);
  }

  @Test
  public void testMultiServer() {
    Map<QuicServer, Context> servers = new HashMap<>();
    int num = 3;
    for (int i = 0;i < num;i++) {
      QuicServer server = server();
      server.handler(conn -> {
        servers.put(server, Vertx.currentContext());
      });
      server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    }
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    Set<QuicServer> closed = new HashSet<>();
    for (int i = 0;i < num;i++) {
      int inflight = servers.size();
      QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
      connection.close().await();
      assertWaitUntil(() -> servers.size() == inflight + 1);
      Set<QuicServer> set = new HashSet<>(servers.keySet());
      set.removeAll(closed);
      Iterator<QuicServer> it = set.iterator();
      QuicServer toClose = it.next();
      assertFalse(it.hasNext());
      toClose.close().await();
      closed.add(toClose);
    }
    LocalMap<Object, Object> map = vertx.sharedData().getLocalMap(QuicServerImpl.QUIC_SERVER_MAP_KEY);
    assertWaitUntil(() -> map.isEmpty());
  }

  @Test
  public void testAddServers() throws Exception {
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    Map<QuicServer, Context> servers = new HashMap<>();
    VertxInternal vxi = (VertxInternal) vertx;
    int num = 3;
    List<QuicConnection> connections = new ArrayList<>();
    for (int i = 0;i < num;i++) {
      QuicServer server = server();
      server.handler(conn -> {
        conn.handler(stream -> {
          stream.endHandler(v -> {
            stream.end();
          });
        });
        servers.put(server, Vertx.currentContext());
      });
      Context ctx = vxi.createEventLoopContext();
      Future.future(p -> ctx.runOnContext(v -> server
        .bind(SocketAddress.inetSocketAddress(9999, "localhost"))
        .onComplete(p)))
        .await();
      QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
      connections.add(connection);
    }
    for (QuicConnection connection : connections) {
      QuicStream stream = connection.openStream().await();
      CountDownLatch latch = new CountDownLatch(1);
      stream.endHandler(v -> {
        latch.countDown();
      });
      stream.end().await();
      awaitLatch(latch, 10, TimeUnit.SECONDS);
    }
  }
}
