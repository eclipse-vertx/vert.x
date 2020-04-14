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

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.ReadStream;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpRequestStreamTest extends VertxTestBase {

  private HttpServer server;
  private NetClient netClient;
  private HttpClient client;

  @Override
  protected void tearDown() throws Exception {
    if (netClient != null) {
      netClient.close();
    }
    if (client != null) {
      client.close();
    }
    if (server != null) {
      CountDownLatch latch = new CountDownLatch(1);
      server.close((asyncResult) -> {
        assertTrue(asyncResult.succeeded());
        latch.countDown();
      });
      awaitLatch(latch);
    }
    super.tearDown();
  }

  @Test
  public void testReadStreamPauseResume() {
    String path = "/some/path";
    this.server = vertx.createHttpServer(new HttpServerOptions().setAcceptBacklog(10).setPort(HttpTestBase.DEFAULT_HTTP_PORT));
    ReadStream<HttpServerRequest> httpStream = server.requestStream();
    AtomicBoolean paused = new AtomicBoolean();
    httpStream.handler(req -> {
      assertFalse(paused.get());
      HttpServerResponse response = req.response();
      response.setStatusCode(200).end();
      response.close();
    });
    server.listen(listenAR -> {
      assertTrue(listenAR.succeeded());
      paused.set(true);
      httpStream.pause();

      netClient = vertx.createNetClient(new NetClientOptions().setConnectTimeout(1000));
      netClient.connect(HttpTestBase.DEFAULT_HTTP_PORT, "localhost", socketAR -> {
        assertTrue(socketAR.succeeded());
        NetSocket socket = socketAR.result();
        socket.write("GET / HTTP/1.1\r\n\r\n");
        Buffer buffer = Buffer.buffer();
        socket.handler(buffer::appendBuffer);
        socket.closeHandler(v -> {
          assertEquals(0, buffer.length());
          paused.set(false);
          httpStream.resume();
          client = vertx.createHttpClient(new HttpClientOptions());
          client.get(HttpTestBase.DEFAULT_HTTP_PORT, "localhost", path, onSuccess(resp -> {
            assertEquals(200, resp.statusCode());
            testComplete();
          }));
        });
      });
    });
    await();
  }

  @Test
  public void testClosingServerClosesRequestStreamEndHandler() {
    waitFor(2);
    this.server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT));
    ReadStream<HttpServerRequest> stream = server.requestStream();
    stream.endHandler(v -> complete());
    stream.handler(req -> {});
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      server.close(v -> {
        assertTrue(ar.succeeded());
        complete();
      });
    });
    await();
  }

  @Test
  public void testCloseServerAsynchronously() {
    this.server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT));
    AtomicInteger done = new AtomicInteger();
    ReadStream<HttpServerRequest> stream = server.requestStream();
    stream.handler(req -> {});
    ThreadLocal<Object> stack = new ThreadLocal<>();
    stack.set(true);
    stream.endHandler(v -> {
      assertTrue(Vertx.currentContext().isEventLoopContext());
      assertNull(stack.get());
      if (done.incrementAndGet() == 2) {
        testComplete();
      }
    });
    server.listen(ar -> {
      assertTrue(Vertx.currentContext().isEventLoopContext());
      assertNull(stack.get());
      server.close(v -> {
        assertTrue(Vertx.currentContext().isEventLoopContext());
        if (done.incrementAndGet() == 2) {
          testComplete();
        }
      });
    });
    await();
  }
}
