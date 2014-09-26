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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpStream;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpRequestStreamTest extends NetTestBase {

  private HttpServer server;
  private NetClient netClient;

  @Override
  protected void tearDown() throws Exception {
    if (netClient != null) {
      netClient.close();
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
  public void testPausedRequestStreamWithFilledBacklogFailsConnecting() {
    String path = "/some/path";
    this.server = vertx.createHttpServer(new HttpServerOptions().setAcceptBacklog(10).setPort(HttpTestBase.DEFAULT_HTTP_PORT));
    HttpStream<HttpServerRequest> stream = server.requestStream();
    AtomicInteger count = new AtomicInteger();
    AtomicBoolean paused = new AtomicBoolean();
    stream.handler(req -> {
      assertFalse(paused.get());
      HttpServerResponse response = req.response();
      response.setStatusCode(200).end();
      response.close();
    });
    server.listen(listenAR -> {
      assertTrue(listenAR.succeeded());
      paused.set(true);
      stream.pause();
      netClient = vertx.createNetClient(new NetClientOptions().setConnectTimeout(1000));
      Runnable[] r = new Runnable[1];
      (r[0] = () -> {
        netClient.connect(HttpTestBase.DEFAULT_HTTP_PORT, "localhost", socketAR -> {
          if (socketAR.succeeded()) {
            NetSocket socket = socketAR.result();
            socket.write(Buffer.buffer(
              "GET " + path + " HTTP/1.1\r\n" +
              "Host: localhost:8080\r\n" +
              "\r\n"
            ));
            count.incrementAndGet();
            StringBuilder sb = new StringBuilder();
            socket.handler(data -> sb.append(data.toString("UTF-8")));
            socket.closeHandler(v2 -> {
              String expectedPrefix = "HTTP/1.1 200 OK\r\n";
              assertTrue("Was expecting <" + sb + "> to start with <" + expectedPrefix + ">", sb.toString().startsWith(expectedPrefix));
              if (count.decrementAndGet() == 0) {
                testComplete();
              }
            });
            r[0].run();
          } else {
            paused.set(false);
            stream.resume();
          }
        });
      }).run();
    });
    await();
  }

  @Test
  public void testResumePausedRequestStream() {
    String path = "/some/path";
    ArrayList<String> messages = new ArrayList<>();
    this.server = vertx.createHttpServer(new HttpServerOptions().setAcceptBacklog(1).setPort(HttpTestBase.DEFAULT_HTTP_PORT));
    HttpStream<HttpServerRequest> stream = server.requestStream();
    stream.handler(req -> {
      StringBuilder data = new StringBuilder();
      req.handler(event -> {
        data.append(event.toString("UTF-8"));
      });
      req.endHandler(v -> {
        String expectedData = messages.stream().reduce(String::concat).get();
        if (!data.toString().equals(expectedData)) {
          fail("Not same data");
        }
        HttpServerResponse response = req.response();
        response.setStatusCode(200).end();
        response.close();
        testComplete();
      });
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      stream.pause();
      netClient = vertx.createNetClient(new NetClientOptions().setConnectTimeout(100));
      netClient.connect(HttpTestBase.DEFAULT_HTTP_PORT, "localhost", socketAR -> {
        assertTrue(socketAR.succeeded());
        NetSocket socket = socketAR.result();
        socket.write(Buffer.buffer(
            "PUT " + path + " HTTP/1.1\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Host: localhost:8080\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n"
        ));
        // We do PUT and write in the socket until it is full
        while (true) {
          if (socket.writeQueueFull()) {
            // When the socket is full we resume the stream and we finish the PUT
            stream.resume();
            socket.write("0\r\n\r\n");
            break;
          } else {
            String message = TestUtils.randomAlphaString(30);
            messages.add(message);
            socket.write(Integer.toHexString(message.length()));
            socket.write("\r\n");
            socket.write(message, "UTF-8");
            socket.write("\r\n");
          }
        }
      });
    });
    await();
  }

  @Test
  public void testClosingServerClosesRequestStreamEndHandler() {
    this.server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT));
    HttpStream<HttpServerRequest> stream = server.requestStream();
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
}
