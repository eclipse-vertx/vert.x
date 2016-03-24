/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.StreamResetException;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2Test extends HttpTest {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    client = vertx.createHttpClient(createBaseClientOptions());
    server = vertx.createHttpServer(createBaseServerOptions().setHandle100ContinueAutomatically(true));
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return Http2TestBase.createHttp2ServerOptions(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return Http2TestBase.createHttp2ClientOptions();
  }

  // Extra test

  @Test
  public void testServerResponseWriteBufferFromOtherThread() throws Exception {
    server.requestHandler(req -> {
      runAsync(() -> {
        req.response().write("hello ").end("world");
      });
    }).listen(onSuccess(v -> {
      client.get(8080, "localhost", "/somepath", resp -> {
        assertEquals(200, resp.statusCode());
        resp.bodyHandler(buff -> {
          assertEquals(Buffer.buffer("hello world"), buff);
          testComplete();
        });
      }).exceptionHandler(this::fail).end();
    }));
    await();
  }

  @Test
  public void testServerResponseResetFromOtherThread() throws Exception {
    server.requestHandler(req -> {
      runAsync(() -> {
        req.response().reset(0);
      });
    }).listen(onSuccess(v -> {
      client.get(8080, "localhost", "/somepath", resp -> {
        fail();
      }).exceptionHandler(err -> {
        assertTrue(err instanceof StreamResetException);
        testComplete();
      }).sendHead();
    }));
    await();
  }

  void runAsync(Runnable runnable) {
    new Thread(() -> {
      try {
        runnable.run();
      } catch (Exception e) {
        fail(e);
      }
    }).start();
  }

  @Test
  public void testClientRequestWriteFromOtherThread() throws Exception {
    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(1);
    server.requestHandler(req -> {
      latch2.countDown();
      req.endHandler(v -> {
        req.response().end();
      });
    }).listen(onSuccess(v -> {
      latch1.countDown();
    }));
    awaitLatch(latch1);
    HttpClientRequest req = client.get(8080, "localhost", "/somepath", resp -> {
      assertEquals(200, resp.statusCode());
      testComplete();
    }).setChunked(true).sendHead();
    awaitLatch(latch2); // The next write won't be buffered
    req.write("hello ").end("world");
    await();
  }
}
