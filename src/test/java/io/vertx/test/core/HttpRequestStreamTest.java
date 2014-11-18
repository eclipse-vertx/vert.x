/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.test.core;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerRequestStream;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.ReadStream;
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
        Buffer buffer = Buffer.buffer();
        socket.handler(buffer::appendBuffer);
        socket.closeHandler(v -> {
          assertEquals(0, buffer.length());
          paused.set(false);
          httpStream.resume();
          client = vertx.createHttpClient(new HttpClientOptions());
          client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, "localhost", path, resp -> {
            assertEquals(200, resp.statusCode());
            testComplete();
          }).end();
        });
      });
    });
    await();
  }

  @Test
  public void testClosingServerClosesRequestStreamEndHandler() {
    this.server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT));
    ReadStream<HttpServerRequest> stream = server.requestStream();
    AtomicBoolean closed = new AtomicBoolean();
    stream.endHandler(v -> closed.set(true));
    stream.handler(req -> {});
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      assertFalse(closed.get());
      server.close(v -> {
        assertTrue(ar.succeeded());
        assertTrue(closed.get());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testCloseServerAsynchronously() {
    this.server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT));
    AtomicInteger done = new AtomicInteger();
    HttpServerRequestStream stream = server.requestStream();
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
      ThreadLocal<Object> stack2 = new ThreadLocal<>();
      stack2.set(true);
      server.close(v -> {
        assertTrue(Vertx.currentContext().isEventLoopContext());
        assertNull(stack2.get());
        if (done.incrementAndGet() == 2) {
          testComplete();
        }
      });
      stack2.set(null);
    });
    await();
  }
}
