/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.benchmark;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.Http1xServerConnection;
import io.vertx.core.http.impl.VertxHttpRequestDecoder;
import io.vertx.core.http.impl.VertxHttpResponseEncoder;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

public class Http1xServerConnectionTest extends VertxTestBase {

  private static final String HELLO_WORLD = "Hello, world!";
  private static final Buffer HELLO_WORLD_BUFFER = Buffer.buffer(HELLO_WORLD);

  @Test
  public void testSimple() {

    Handler<HttpServerRequest> app = request -> {
      HttpServerResponse response = request.response();
      response.end(HELLO_WORLD_BUFFER);
    };

    VertxInternal vertx = (VertxInternal) this.vertx;

    HttpServerOptions options = new HttpServerOptions();
    EmbeddedChannel vertxChannel = new EmbeddedChannel(
      new VertxHttpRequestDecoder(options),
      new VertxHttpResponseEncoder());
    vertxChannel.config().setAllocator(new io.vertx.benchmarks.HttpServerHandlerBenchmark.Alloc());

    ContextInternal context = vertx
      .contextBuilder()
      .withEventLoop(vertxChannel.eventLoop())
      .withClassLoader(Thread.currentThread().getContextClassLoader())
      .build();


    VertxHandler<Http1xServerConnection> handler = VertxHandler.create(chctx -> {
      Http1xServerConnection conn = new Http1xServerConnection(
        () -> context,
        null,
        new HttpServerOptions(),
        chctx,
        context,
        "localhost",
        null);
      conn.handler(app);
      return conn;
    });
    vertxChannel.pipeline().addLast("handler", handler);

    ByteBuf GET = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer((
      "GET / HTTP/1.1\r\n" +
        "\r\n").getBytes()));
    vertxChannel.writeInbound(GET);
    vertxChannel.outboundMessages().poll();

  }

}
