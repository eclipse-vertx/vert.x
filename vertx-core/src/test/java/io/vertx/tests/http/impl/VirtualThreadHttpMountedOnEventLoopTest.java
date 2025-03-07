/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.Http1xServerConnection;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static io.vertx.test.http.HttpTestBase.DEFAULT_HTTP_HOST;
import static io.vertx.test.http.HttpTestBase.DEFAULT_HTTP_PORT;

public class VirtualThreadHttpMountedOnEventLoopTest extends VirtualThreadHttpTestBase {

  protected ContextInternal createVirtualThreadContext() {
    return vertx.createContext(ThreadingModel.VIRTUAL_THREAD_MOUNTED_ON_EVENT_LOOP);
  }

  @Test
  public void testPipeliningWriteBatching() {

    DeploymentOptions options = new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD_MOUNTED_ON_EVENT_LOOP);

    AtomicInteger numFlushes = new AtomicInteger();

    vertx.deployVerticle(ctx -> vertx
        .createHttpServer()
        .connectionHandler(conn -> {
          Http1xServerConnection http1xConn = (Http1xServerConnection) conn;
          ChannelPipeline pipeline = http1xConn.channel().pipeline();
          pipeline.addFirst(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
              ctx.write(msg, promise);
            }
            @Override
            public void flush(ChannelHandlerContext ctx) throws Exception {
              numFlushes.incrementAndGet();
              ctx.flush();
            }
          });
        })
        .requestHandler(req -> {
          HttpServerResponse resp = req.response();
          resp.end("Hello World");
        })
        .listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST), options)
      .await();

    int numRequests = 16;

    StringBuilder aggregatedRequests = new StringBuilder();
    for (int i = 0;i < numRequests;i++) {
      aggregatedRequests.append(
        """
          GET / HTTP/1.1\r
          host: localhost:8080\r
          content-length: 0\r
          \r
          """);
    }

    NetClient client = vertx.createNetClient();

    NetSocket so = client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).await();

    StringBuilder responses = new StringBuilder();
    AtomicInteger numResponses = new AtomicInteger();
    AtomicInteger curr = new AtomicInteger();
    so.handler(buff -> {
      responses.append(buff.toString());
      while (true) {
        int idx = responses.indexOf("\r\n\r\n", curr.get());
        if (idx == -1) {
          break;
        }
        numResponses.incrementAndGet();
        curr.set(idx + 4);
      }
      if (numResponses.get() == numRequests) {
        // We can expect one if we do execute directly tasks with no delay in VirtualThreadMountedOnEventLoopExecutor
        assertEquals(16, numFlushes.get());
        testComplete();
      }
    });

    so.write(aggregatedRequests.toString()).await();

    await();

  }

  @Test
  public void testWriteBatching() {

    DeploymentOptions options = new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD_MOUNTED_ON_EVENT_LOOP);

    AtomicInteger numFlushes = new AtomicInteger();
    int numChunks = 32;

    vertx.deployVerticle(ctx -> vertx
        .createHttpServer()
        .connectionHandler(conn -> {
          Http1xServerConnection http1xConn = (Http1xServerConnection) conn;
          ChannelPipeline pipeline = http1xConn.channel().pipeline();
          pipeline.addFirst(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
              ctx.write(msg, promise);
            }
            @Override
            public void flush(ChannelHandlerContext ctx) throws Exception {
              numFlushes.incrementAndGet();
              ctx.flush();
            }
          });
        })
        .requestHandler(req -> {
          HttpServerResponse resp = req.response();
          resp.setChunked(true);
          for (int i = 0;i < 32;i++) {
            resp.write("chunk-" + i);
          }
          resp.end();
        })
        .listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST), options)
      .await();

    HttpClientAgent client = vertx.createHttpClient();
    Buffer response = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
      .compose(req -> req.send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .await();

    StringBuilder expected = new StringBuilder();
    for (int i = 0;i < numChunks;i++) {
      expected.append("chunk-" + i);
    }

    assertEquals(expected.toString(), response.toString());
    assertEquals(1, numFlushes.get());
  }
}
