/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.it.networklogging;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.LogConfig;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.QuicClient;
import io.vertx.core.net.QuicClientConfig;
import io.vertx.core.net.QuicConnection;
import io.vertx.core.net.QuicServer;
import io.vertx.core.net.QuicServerConfig;
import io.vertx.core.net.QuicStream;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.netty.TestLoggerFactory;
import io.vertx.tests.net.quic.QuicClientTest;
import org.junit.Test;

import java.util.function.Predicate;

import static io.vertx.tests.net.quic.QuicServerTest.SSL_OPTIONS;

public class NetTest extends VertxTestBase {

  @Test
  public void testNoLogging() throws Exception {
    testLogging(new NetServerOptions(), new NetClientOptions(), factory -> !factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  @Test
  public void testServerLogging() throws Exception {
    testLogging(new NetServerOptions().setLogActivity(true), new NetClientOptions(), factory -> factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  @Test
  public void testClientLogging() throws Exception {
    testLogging(new NetServerOptions(), new NetClientOptions().setLogActivity(true), factory -> factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  public void testLogging(NetServerOptions serverOptions, NetClientOptions clientOptions, Predicate<TestLoggerFactory> checker) throws Exception {
    NetServer server;
    NetClient client;
    server = vertx.createNetServer(serverOptions);
    client = vertx.createNetClient(clientOptions);
    TestUtils.testLogging(factory -> {
      server.connectHandler(so -> {
        so.end(Buffer.buffer("fizzbuzz"));
      });
      server.listen(0, "localhost").await();
      NetSocket so = client.connect(server.actualPort(), "localhost").await();
      so.closeHandler(v2 -> testComplete());
      await();
    });
  }
}
