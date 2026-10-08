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

package io.vertx.core.http;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.net.impl.ConnectionBase;
import org.junit.Test;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Http1xQueuedRequestCloseTest {

  @Test
  public void closeHandlerRunsWhenQueuedRequestHasNoResponse() throws Exception {
    Vertx vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(1).setWorkerPoolSize(1));
    HttpServer server = vertx.createHttpServer(new HttpServerOptions().setHost("127.0.0.1").setPort(0));
    CountDownLatch firstRequest = new CountDownLatch(1);
    CountDownLatch queuedRequest = new CountDownLatch(1);
    CountDownLatch channelClosed = new CountDownLatch(1);
    CountDownLatch closeCallback = new CountDownLatch(1);
    AtomicInteger callbacks = new AtomicInteger();
    AtomicInteger dispatched = new AtomicInteger();
    try {
      server.connectionHandler(connection -> {
        connection.closeHandler(ignored -> {
          callbacks.incrementAndGet();
          closeCallback.countDown();
        });
        Channel channel = ((ConnectionBase) connection).channel();
        channel.closeFuture().addListener(ignored -> channelClosed.countDown());
        channel.pipeline().addBefore("handler", "observeQueuedRequest", new ChannelInboundHandlerAdapter() {
          private int requests;

          @Override
          public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
            boolean secondRequest = message instanceof HttpRequest && ++requests == 2;
            context.fireChannelRead(message);
            if (secondRequest) {
              queuedRequest.countDown();
            }
          }
        });
      });
      server.requestHandler(request -> {
        dispatched.incrementAndGet();
        firstRequest.countDown();
        // Keep the first response open so the second request has no response yet.
      });
      server.listen().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
      try (Socket client = new Socket("127.0.0.1", server.actualPort())) {
        client.getOutputStream().write("GET /first HTTP/1.1\r\nHost: localhost\r\n\r\n"
          .getBytes(StandardCharsets.US_ASCII));
        client.getOutputStream().flush();
        assertTrue("First request was not dispatched", firstRequest.await(5, TimeUnit.SECONDS));
        client.getOutputStream().write("POST /second HTTP/1.1\r\nHost: localhost\r\nContent-Length: 1\r\n\r\n"
          .getBytes(StandardCharsets.US_ASCII));
        client.getOutputStream().flush();
        assertTrue("Second request was not queued", queuedRequest.await(5, TimeUnit.SECONDS));
        assertEquals("Second request must remain undispatched", 1, dispatched.get());
      }
      assertTrue("Underlying channel did not close", channelClosed.await(5, TimeUnit.SECONDS));
      assertTrue("Vert.x close callback did not run", closeCallback.await(2, TimeUnit.SECONDS));
      assertEquals("Vert.x close callback ran more than once", 1, callbacks.get());
    } finally {
      vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
  }
}
