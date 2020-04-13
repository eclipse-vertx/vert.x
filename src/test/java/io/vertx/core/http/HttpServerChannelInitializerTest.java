/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

public class HttpServerChannelInitializerTest extends VertxTestBase {

  @Test
  public void testHttpServer() throws Exception {
/*
    VertxInternal vertx = (VertxInternal) this.vertx;
    Transport transport = vertx.transport();
    ChannelFactory<? extends ServerChannel> factory = transport.serverChannelFactory(false);
    ServerBootstrap bs = new ServerBootstrap();
    bs.group(vertx.getAcceptorEventLoopGroup(), this.vertx.nettyEventLoopGroup());
    bs.channelFactory(factory);
    bs.childHandler(new HttpServerChannelInitializer(
      vertx,
      new SSLHelper(new HttpServerOptions(), null, null),
      new HttpServerOptions(),
      "http://localhost:8080",
      null,
      false,
      eventLoop -> new HandlerHolder<>(vertx.createEventLoopContext(eventLoop, null, null), conn -> {
        conn.handler(req -> {
          req.response().end("Hello World");
        });
      }),
      eventLoop -> new HandlerHolder<>(vertx.createEventLoopContext(eventLoop, null, null), this::fail))
    );
    ChannelFuture bind = bs.bind(HttpTest.DEFAULT_HTTP_HOST, HttpTest.DEFAULT_HTTP_PORT);
    bind.sync();
    HttpClient client = this.vertx.createHttpClient();
    client.get(HttpTest.DEFAULT_HTTP_PORT, HttpTest.DEFAULT_HTTP_HOST, "/", resp -> {
      testComplete();
    });
    await();
*/
  }

}
