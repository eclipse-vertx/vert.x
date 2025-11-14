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

import io.netty.buffer.ByteBuf;
import io.vertx.core.Completable;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;
import io.vertx.core.net.*;
import io.vertx.core.streams.WriteStream;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;

import static io.vertx.tests.net.quic.QuicClientTest.clientOptions;
import static io.vertx.tests.net.quic.QuicServerTest.serverOptions;

@RunWith(LinuxOrOsx.class)
public class QuicFlowControlTest extends VertxTestBase {

  private QuicServer server;
  private QuicClient client;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    server = QuicServer.create(vertx, serverOptions());
    client = QuicClient.create(vertx, clientOptions());
  }

  @Override
  protected void tearDown() throws Exception {
    client.close().await();
    server.close().await();
    super.tearDown();
  }

  private void pump(int times, Buffer chunk, WriteStream<Buffer> writeStream, Completable<Integer> cont) {
    if (writeStream.writeQueueFull()) {
      cont.succeed(times);
    } else {
      writeStream.write(chunk);
      vertx.runOnContext(v -> pump(times + 1, chunk, writeStream, cont));
    }
  }

  @Test
  public void testFlowControl() {
    CompletableFuture<Integer> latch = new CompletableFuture<>();
    Buffer chunk = Buffer.buffer(TestUtils.randomAlphaString(128));
    server.handler(conn -> {
      conn.streamHandler(stream -> {
        pump(0, chunk, stream, onSuccess2(times -> {
          stream.end();
          latch.complete(times);
        }));
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    connection
      .openStream()
      .onComplete(onSuccess2(stream -> {
        stream.pause();
        QuicStreamInternal streamInternal = (QuicStreamInternal) stream;
        Buffer expected = Buffer.buffer();
        latch.whenComplete((times, err) -> {
          for (int i = 0; i < times; i++) {
            expected.appendBuffer(chunk);
          }
          stream.resume();
        });
        Buffer cumulation = Buffer.buffer();
        streamInternal.messageHandler(msg -> {
          ByteBuf buff = (ByteBuf) msg;
          Buffer buffer = BufferInternal.safeBuffer(buff);
          cumulation.appendBuffer(buffer);
        });
        streamInternal.endHandler(v -> {
          assertEquals(expected, cumulation);
          testComplete();
        });
        stream.write("ping");
      }));

    await();
  }
}
