/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;

import io.netty.channel.socket.SocketChannel;
import io.vertx.core.Context;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.Http2ServerConnection;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.TestUtils;
import io.vertx.test.tls.Cert;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

  @Test
  @Override
  public void testCloseHandlerNotCalledWhenConnectionClosedAfterEnd() throws Exception {
    testCloseHandlerNotCalledWhenConnectionClosedAfterEnd(1);
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

  @Test
  public void testServerOpenSSL() throws Exception {
    HttpServerOptions opts = new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)
        .setUseAlpn(true)
        .setSsl(true)
        .addEnabledCipherSuite("TLS_RSA_WITH_AES_128_CBC_SHA") // Non Diffie-helman -> debuggable in wireshark
        .setPemKeyCertOptions(Cert.SERVER_PEM.get()).setSslEngineOptions(new OpenSSLEngineOptions());
    server.close();
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    server = vertx.createHttpServer(opts);
    server.requestHandler(req -> {
      req.response().end();
    });
    CountDownLatch latch = new CountDownLatch(1);
    System.out.println("starting");
    try {
      server.listen(onSuccess(v -> latch.countDown()));
    } catch (Throwable e) {
      e.printStackTrace();
    }
    System.out.println("listening");
    awaitLatch(latch);
    client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      assertEquals(200, resp.statusCode());
      testComplete();
    }).exceptionHandler(this::fail).end();
    await();
  }

  @Test
  public void testServerStreamPausedWhenConnectionIsPaused() throws Exception {
    CountDownLatch fullLatch = new CountDownLatch(1);
    CompletableFuture<Void> resumeLatch = new CompletableFuture<>();
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      switch (req.path()) {
        case "/0": {
          vertx.setPeriodic(1, timerID -> {
            if (resp.writeQueueFull()) {
              vertx.cancelTimer(timerID);
              fullLatch.countDown();
            } else {
              resp.write(Buffer.buffer(TestUtils.randomAlphaString(512)));
            }
          });
          break;
        }
        case "/1": {
          assertTrue(resp.writeQueueFull());
          resp.drainHandler(v -> {
            resp.end();
          });
          resumeLatch.complete(null);
          break;
        }
      }
    });
    startServer();
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/0", resp -> {
      resp.pause();
      Context ctx = vertx.getOrCreateContext();
      resumeLatch.thenAccept(v1 -> {
        ctx.runOnContext(v2 -> {
          resp.endHandler(v -> {
            testComplete();
          });
          resp.resume();
        });
      });
    });
    awaitLatch(fullLatch);
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/1", resp -> {
      resp.endHandler(v -> {
        complete();
      });
    });
    resumeLatch.get(20, TimeUnit.SECONDS); // Make sure it completes
    await();
  }

  @Test
  public void testClientStreamPausedWhenConnectionIsPaused() throws Exception {
    waitFor(2);
    Buffer buffer = TestUtils.randomBuffer(512);
    CompletableFuture<Void> resumeLatch = new CompletableFuture<>();
    server.requestHandler(req -> {
      switch (req.path()) {
        case "/0": {
          req.pause();
          resumeLatch.thenAccept(v -> {
            req.resume();
          });
          req.endHandler(v -> {
            req.response().end();
          });
          break;
        }
        case "/1": {
          req.bodyHandler(v -> {
            assertEquals(v, buffer);
            req.response().end();
          });
          break;
        }
      }
    });
    startServer();
    HttpClientRequest req1 = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/0", resp -> {
      complete();
    }).setChunked(true);
    while (!req1.writeQueueFull()) {
      req1.write(Buffer.buffer(TestUtils.randomAlphaString(512)));
      Thread.sleep(1);
    }
    HttpClientRequest req2 = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/1", resp -> {
      complete();
    }).setChunked(true);
    assertFalse(req2.writeQueueFull());
    req2.sendHead(v -> {
      assertTrue(req2.writeQueueFull());
      resumeLatch.complete(null);
    });
    resumeLatch.get(20, TimeUnit.SECONDS);
    AsyncTestBase.assertWaitUntil(() -> !req2.writeQueueFull());
    req1.end();
    req2.end(buffer);
    await();
  }

  @Test
  public void testResetClientRequestNotYetSent() throws Exception {
    waitFor(2);
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setInitialSettings(new Http2Settings().setMaxConcurrentStreams(1)));
    AtomicInteger numReq = new AtomicInteger();
    server.requestHandler(req -> {
      assertEquals(0, numReq.getAndIncrement());
      req.response().end();
      complete();
    });
    startServer();
    HttpClientRequest post = client.post(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
      fail();
    });
    post.setChunked(true).write(TestUtils.randomBuffer(1024));
    assertTrue(post.reset());
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
      assertEquals(1, numReq.get());
      complete();
    });
    await();
  }

  @Test
  public void testDiscardConnectionWhenChannelBecomesInactive() throws Exception {
    AtomicInteger count = new AtomicInteger();
    server.requestHandler(req -> {
      if (count.getAndIncrement() == 0) {
        Http2ServerConnection a = (Http2ServerConnection) req.connection();
        SocketChannel channel = (SocketChannel) a.channel();
        channel.shutdown();
      } else {
        req.response().end();
      }
    });
    startServer();
    AtomicBoolean closed = new AtomicBoolean();
    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
      fail();
    }).connectionHandler(conn -> conn.closeHandler(v -> closed.set(true))).end();
    AsyncTestBase.assertWaitUntil(closed::get);
    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
      testComplete();
    }).exceptionHandler(err -> {
      fail();
    }).end();
    await();
  }

  @Test
  public void testClientDoesNotSupportAlpn() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      req.response().end();
      complete();
    });
    startServer();
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1).setUseAlpn(false));
    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
      assertEquals(HttpVersion.HTTP_1_1, resp.version());
      complete();
    }).exceptionHandler(this::fail).end();
    await();
  }

  @Test
  public void testServerDoesNotSupportAlpn() throws Exception {
    waitFor(2);
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setUseAlpn(false));
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      req.response().end();
      complete();
    });
    startServer();
    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
      assertEquals(HttpVersion.HTTP_1_1, resp.version());
      complete();
    }).exceptionHandler(this::fail).end();
    await();
  }

  @Test
  public void testClientMakeRequestHttp2WithSSLWithoutAlpn() throws Exception {
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setUseAlpn(false));
    try {
      client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI);
      fail();
    } catch (IllegalArgumentException ignore) {
      // Expected
    }
  }

  @Test
  public void testServePendingRequests() throws Exception {
    int n = 10;
    waitFor(n);
    LinkedList<HttpServerRequest> requests = new LinkedList<>();
    Set<HttpConnection> connections = new HashSet<>();
    server.requestHandler(req -> {
      requests.add(req);
      connections.add(req.connection());
      assertEquals(1, connections.size());
      if (requests.size() == n) {
        while (requests.size() > 0) {
          requests.removeFirst().response().end();
        }
      }
    });
    startServer();
    for (int i = 0;i < n;i++) {
      client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> complete()).end();
    }
    await();
  }

  @Test
  public void testInitialMaxConcurrentStreamZero() throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setInitialSettings(new Http2Settings().setMaxConcurrentStreams(0)));
    server.requestHandler(req -> {
      req.response().end();
    });
    server.connectionHandler(conn -> {
      vertx.setTimer(500, id -> {
        conn.updateSettings(new Http2Settings().setMaxConcurrentStreams(10));
      });
    });
    startServer();
    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
      testComplete();
    }).connectionHandler(conn -> {
      assertEquals(10, conn.remoteSettings().getMaxConcurrentStreams());
    }).setTimeout(10000).exceptionHandler(this::fail).end();
    await();
  }

  @Test
  public void testFoo() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.write("Hello");
      resp.end("World");
      assertNull(resp.headers().get("content-length"));
      complete();
    });
    startServer();
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
      assertNull(resp.getHeader("content-length"));
      resp.bodyHandler(body -> {
        assertEquals("HelloWorld", body.toString());
        complete();
      });
    });
    await();
  }

  @Test
  public void testKeepAliveTimeout() throws Exception {
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer();
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setHttp2KeepAliveTimeout(3).setPoolCleanerPeriod(1));
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
      long now = System.currentTimeMillis();
      resp.request().connection().closeHandler(v -> {
        long timeout = System.currentTimeMillis() - now;
        int delta = 500;
        int low = 3000 - delta;
        int high = 3000 + delta;
        assertTrue("Expected actual close timeout " + timeout + " to be > " + low, low < timeout);
        assertTrue("Expected actual close timeout " + timeout + " to be < " + high, timeout < high);
        testComplete();
      });
    });
    await();
  }
}
