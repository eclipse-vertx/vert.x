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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.net.*;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

import static io.vertx.tests.net.quic.QuicClientTest.clientOptions;
import static io.vertx.tests.net.quic.QuicServerTest.serverOptions;

@RunWith(LinuxOrOsx.class)
public class QuicContextTest extends VertxTestBase {

  private ContextInternal workerContext;
  private QuicServer server;
  private QuicClient client;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    QuicServerOptions serverOptions = serverOptions();
    QuicClientOptions clientOptions = clientOptions();
    serverOptions.getTransportOptions().setEnableDatagrams(true);
    clientOptions.getTransportOptions().setEnableDatagrams(true);
    server = QuicServer.create(vertx, serverOptions);
    client = QuicClient.create(vertx, clientOptions);
    workerContext = ((VertxInternal) vertx).createWorkerContext();
  }

  @Override
  protected void tearDown() throws Exception {
    client.close().await();
    server.close().await();
    super.tearDown();
  }

  @Test
  public void testServerConnectionScoped() {

    server.handler(conn -> {
      assertSame(Vertx.currentContext(), workerContext);
      conn.streamHandler(stream -> {
        assertSame(Vertx.currentContext(), workerContext);
        stream.handler(buff -> {
          assertSame(Vertx.currentContext(), workerContext);
          stream.write(buff);
        });
        stream.endHandler(v -> {
          assertSame(Vertx.currentContext(), workerContext);
          stream.end();
          testComplete();
        });
      });
    });

    Future.future(p -> workerContext.runOnContext(v -> server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).onComplete(p))).await();
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicStream stream = connection
      .createStream().await();
    stream.end(Buffer.buffer("ping")).await();
    await();
  }

  @Test
  public void testServerStreamScoped() {

    server.handler(conn -> {
      assertSame(Vertx.currentContext(), workerContext);
      conn.streamHandler(stream -> {
        assertSame(Vertx.currentContext(), workerContext);
        stream.handler(buff -> {
          assertSame(Vertx.currentContext(), workerContext);
          stream.write(buff);
        });
        stream.endHandler(v -> {
          assertSame(Vertx.currentContext(), workerContext);
          stream.end();
          testComplete();
        });
      });
    });

    Future.future(p -> workerContext.runOnContext(v -> server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).onComplete(p))).await();
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicStream stream = connection.createStream().await();
    stream.end(Buffer.buffer("ping")).await();
    await();
  }

  @Test
  public void testClientConnectionScoped() {

    server.handler(conn -> {
      conn.datagramHandler(conn::writeDatagram);
    });

    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();

    QuicConnection connection = Future.<QuicConnection>future(p -> workerContext.runOnContext(v -> client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).onComplete(p))).await();

    connection.datagramHandler(buff -> {
      assertSame(workerContext, Vertx.currentContext());
      testComplete();
    });
    connection.writeDatagram(Buffer.buffer("ping")).await();

    await();
  }

  @Test
  public void testClientStreamScoped() {

    server.handler(conn -> {
      conn.streamHandler(stream -> {
        stream.handler(buff -> stream.write(buff));
        stream.endHandler(v -> stream.end());
      });
    });

    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();

    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();

    QuicStream stream = Future.<QuicStream>future(p -> workerContext.runOnContext(v -> connection.createStream().onComplete(p))).await();

    AtomicInteger cnt = new AtomicInteger();
    stream.handler(buff -> {
      assertSame(workerContext, Vertx.currentContext());
      cnt.incrementAndGet();
    });
    stream.endHandler(v -> {
      assertSame(workerContext, Vertx.currentContext());
      testComplete();
    });
    stream.write(Buffer.buffer("ping")).await();
    assertWaitUntil(() -> cnt.get() == 1);
    stream.end().await();

    await();
  }

  @Test
  public void testStreamContextProvider() {

    server.handler(conn -> {
      assertNotSame(Vertx.currentContext(), workerContext);
      Context connectionCtx = vertx.getOrCreateContext();
      ((QuicConnectionInternal)conn).streamContextProvider(ctx -> workerContext);
      conn.streamHandler(stream -> {
        assertSame(Vertx.currentContext(), connectionCtx);
        stream.handler(buff -> {
          assertSame(Vertx.currentContext(), workerContext);
          stream.write(buff);
        });
        stream.endHandler(v -> {
          assertSame(Vertx.currentContext(), workerContext);
          stream.end();
          testComplete();
        });
      });
    });

    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicStream stream = connection.createStream().await();
    stream.end(Buffer.buffer("ping")).await();
    await();
  }

  @Test
  public void testStreamContextProvided() {

    server.handler(conn -> {
      conn.streamHandler(stream -> {
        stream.handler(buff -> stream.write(buff));
        stream.endHandler(v -> stream.end());
      });
    });

    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();

    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();

    QuicStream stream = ((QuicConnectionInternal)connection).createStream(workerContext).await();

    AtomicInteger cnt = new AtomicInteger();
    stream.handler(buff -> {
      assertSame(workerContext, Vertx.currentContext());
      cnt.incrementAndGet();
    });
    stream.endHandler(v -> {
      assertSame(workerContext, Vertx.currentContext());
      testComplete();
    });
    stream.write(Buffer.buffer("ping")).await();
    assertWaitUntil(() -> cnt.get() == 1);
    stream.end().await();

    await();  }
}
