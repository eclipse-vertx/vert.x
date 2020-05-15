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

import io.netty.channel.socket.DuplexChannel;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.Http2ServerConnection;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.TestUtils;
import io.vertx.test.tls.Cert;
import org.junit.Test;

import javax.net.ssl.SSLHandshakeException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2Test extends HttpTest {

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
  public void testServerResponseWriteBufferFromOtherThread() {
    server.requestHandler(req -> {
      runAsync(() -> {
        req.response().write("hello world");
      });
    }).listen(testAddress, onSuccess(v -> {
      client.request(HttpMethod.GET, testAddress, 8080, "localhost", "/somepath")
        .onComplete(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          resp.handler(buff -> {
            assertEquals(Buffer.buffer("hello world"), buff);
            testComplete();
          });
        }))
        .exceptionHandler(this::fail)
        .end();
    }));
    await();
  }

  @Test
  public void testServerResponseEndFromOtherThread() {
    server.requestHandler(req -> {
      runAsync(() -> {
        req.response().end();
      });
    }).listen(testAddress, onSuccess(v1 -> {
      client.request(HttpMethod.GET, testAddress, 8080, "localhost", "/somepath")
        .onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        resp.endHandler(v2 -> {
          testComplete();
        });
      })).end();
    }));
    await();
  }

  @Test
  public void testServerResponseEndWithTrailersFromOtherThread() {
    server.requestHandler(req -> {
      runAsync(() -> {
        req.response().putTrailer("some", "trailer").end();
      });
    }).listen(testAddress, onSuccess(v1 -> {
      client.request(HttpMethod.GET, testAddress, 8080, "localhost", "/somepath")
        .onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        resp.endHandler(v2 -> {
          assertEquals(1, resp.trailers().size());
          assertEquals("trailer", resp.trailers().get("some"));
          testComplete();
        });
      })).end();
    }));
    await();
  }

  @Test
  public void testServerResponseResetFromOtherThread() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      runAsync(() -> {
        req.response().reset(0);
      });
    }).listen(testAddress, onSuccess(v -> {
      client.request(HttpMethod.GET, testAddress, 8080, "localhost", "/somepath")
        .onComplete(onFailure(err -> {
          assertTrue(err instanceof StreamResetException);
          complete();
        }))
        .exceptionHandler(err -> {
          assertTrue(err instanceof StreamResetException);
          complete();
        })
        .sendHead();
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
    }).listen(testAddress, onSuccess(v -> {
      latch1.countDown();
    }));
    awaitLatch(latch1);
    HttpClientRequest req = client.request(HttpMethod.GET, testAddress, 8080, "localhost", "/somepath")
      .onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      }))
      .setChunked(true);
    req.sendHead();
    awaitLatch(latch2); // The next write won't be buffered
    req.write("hello ");
    req.end("world");
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
      .setPemKeyCertOptions(Cert.SERVER_PEM.get())
      .setSslEngineOptions(new OpenSSLEngineOptions());
    server.close();
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    server = vertx.createHttpServer(opts);
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath")
      .onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      }))
      .exceptionHandler(this::fail)
      .end();
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
    startServer(testAddress);
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/0")
      .onComplete(onSuccess(resp -> {
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
      }))
      .end();
    awaitLatch(fullLatch);
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/1")
      .onComplete(onSuccess(resp -> {
        resp.endHandler(v -> {
          complete();
        });
      }))
      .end();
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
    startServer(testAddress);
    HttpClientRequest req1 = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/0")
      .onComplete(resp -> {
        complete();
      })
      .setChunked(true);
    CountDownLatch l = new CountDownLatch(1);
    req1.sendHead(onSuccess(v -> {
      vertx.setPeriodic(1 , id -> {
        req1.write(Buffer.buffer(TestUtils.randomAlphaString(512)));
        if (req1.writeQueueFull()) {
          req1.writeQueueFull();
          vertx.cancelTimer(id);
          l.countDown();
        }
      });
    }));
    awaitLatch(l);
    HttpClientRequest req2 = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/1")
      .onComplete(resp -> {
        complete();
      })
      .setChunked(true);
    assertFalse(req2.writeQueueFull());
    req2.sendHead();
    waitUntil(req2::writeQueueFull);
    resumeLatch.complete(null);
    AsyncTestBase.assertWaitUntil(() -> !req2.writeQueueFull());
    req1.end();
    req2.end(buffer);
    await();
  }

  @Test
  public void testResetClientRequestNotYetSent() throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setInitialSettings(new Http2Settings().setMaxConcurrentStreams(1)));
    AtomicInteger numReq = new AtomicInteger();
    server.requestHandler(req -> {
      fail();
    });
    startServer(testAddress);
    // There might be a race between the request write and the request reset
    // so we do it on the context thread to avoid it
    vertx.runOnContext(v -> {
      HttpClientRequest post = client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .onComplete(onFailure(err -> {
        complete();
      }))
        .setChunked(true);
      post.write(TestUtils.randomBuffer(1024));
      assertTrue(post.reset());
    });
    await();
  }

  @Test
  public void testDiscardConnectionWhenChannelBecomesInactive() throws Exception {
    AtomicInteger count = new AtomicInteger();
    server.requestHandler(req -> {
      if (count.getAndIncrement() == 0) {
        Http2ServerConnection a = (Http2ServerConnection) req.connection();
        DuplexChannel channel = (DuplexChannel) a.channel();
        channel.shutdown();
      } else {
        req.response().end();
      }
    });
    startServer(testAddress);
    AtomicInteger closed = new AtomicInteger();
    client.connectionHandler(conn -> conn.closeHandler(v -> closed.incrementAndGet()));
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .onComplete(onFailure(err -> {}))
      .end();
    AsyncTestBase.assertWaitUntil(() -> closed.get() == 1);
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .onComplete(resp -> {
        testComplete();
      })
      .exceptionHandler(err -> {
        fail();
      })
      .end();
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
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1).setUseAlpn(false));
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .onComplete(onSuccess(resp -> {
        assertEquals(HttpVersion.HTTP_1_1, resp.version());
        complete();
      }))
      .exceptionHandler(this::fail)
      .end();
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
    startServer(testAddress);
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .onComplete(onSuccess(resp -> {
        assertEquals(HttpVersion.HTTP_1_1, resp.version());
        complete();
      }))
      .exceptionHandler(this::fail)
      .end();
    await();
  }

  @Test
  public void testClientMakeRequestHttp2WithSSLWithoutAlpn() throws Exception {
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setUseAlpn(false));
    try {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI);
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
    startServer(testAddress);
    for (int i = 0;i < n;i++) {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .onComplete(resp -> complete())
        .end();
    }
    await();
  }

  @Test
  public void testInitialMaxConcurrentStreamZero() throws Exception {
    waitFor(2);
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
    startServer(testAddress);
    client.connectionHandler(conn -> {
      assertEquals(0, conn.remoteSettings().getMaxConcurrentStreams());
      conn.remoteSettingsHandler(settings -> {
        assertEquals(10, conn.remoteSettings().getMaxConcurrentStreams());
        complete();
      });
    });
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .onComplete(resp -> {
        complete();
      })
      .setTimeout(10000)
      .exceptionHandler(this::fail)
      .end();
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
    startServer(testAddress);
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .onComplete(onSuccess(resp -> {
        assertNull(resp.getHeader("content-length"));
        resp.bodyHandler(body -> {
          assertEquals("HelloWorld", body.toString());
          complete();
        });
      }))
      .end();
    await();
  }

  @Test
  public void testStreamWeightAndDependency() throws Exception {
    int requestStreamDependency = 56;
    short requestStreamWeight = 43;
    int responseStreamDependency = 98;
    short responseStreamWeight = 55;
    waitFor(2);
    server.requestHandler(req -> {
      assertEquals(requestStreamWeight, req.streamPriority().getWeight());
      assertEquals(requestStreamDependency, req.streamPriority().getDependency());
      req.response().setStreamPriority(new StreamPriority()
        .setDependency(responseStreamDependency)
        .setWeight(responseStreamWeight)
        .setExclusive(false));
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .onComplete(onSuccess(resp -> {
        assertEquals(responseStreamWeight, resp.request().getStreamPriority().getWeight());
        assertEquals(responseStreamDependency, resp.request().getStreamPriority().getDependency());
        complete();
      }))
      .setStreamPriority(new StreamPriority()
      .setDependency(requestStreamDependency)
      .setWeight(requestStreamWeight)
      .setExclusive(false))
      .end();
    await();
  }

  @Test
  public void testStreamWeightAndDependencyChange() throws Exception {
    int requestStreamDependency = 56;
    short requestStreamWeight = 43;
    int requestStreamDependency2 = 157;
    short requestStreamWeight2 = 143;
    int responseStreamDependency = 98;
    short responseStreamWeight = 55;
    int responseStreamDependency2 = 198;
    short responseStreamWeight2 = 155;
    waitFor(4);
    server.requestHandler(req -> {
      req.streamPriorityHandler( sp -> {
        assertEquals(requestStreamWeight2, sp.getWeight());
        assertEquals(requestStreamDependency2, sp.getDependency());
        assertEquals(requestStreamWeight2, req.streamPriority().getWeight());
        assertEquals(requestStreamDependency2, req.streamPriority().getDependency());
        complete();
      });
      assertEquals(requestStreamWeight, req.streamPriority().getWeight());
      assertEquals(requestStreamDependency, req.streamPriority().getDependency());
      req.response().setStreamPriority(new StreamPriority()
        .setDependency(responseStreamDependency)
        .setWeight(responseStreamWeight)
        .setExclusive(false));
      req.response().write("hello");
      req.response().setStreamPriority(new StreamPriority()
        .setDependency(responseStreamDependency2)
        .setWeight(responseStreamWeight2)
        .setExclusive(false));
      req.response().drainHandler(h -> {});
      req.response().end("world");
      complete();
    });
    startServer(testAddress);
    client = vertx.createHttpClient(createBaseClientOptions());
    HttpClientRequest request = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .onComplete(onSuccess(resp -> {
        assertEquals(responseStreamWeight, resp.request().getStreamPriority().getWeight());
        assertEquals(responseStreamDependency, resp.request().getStreamPriority().getDependency());
        resp.streamPriorityHandler(sp -> {
          assertEquals(responseStreamWeight2, sp.getWeight());
          assertEquals(responseStreamDependency2, sp.getDependency());
          assertEquals(responseStreamWeight2, resp.request().getStreamPriority().getWeight());
          assertEquals(responseStreamDependency2, resp.request().getStreamPriority().getDependency());
          complete();
        });
        complete();
      }))
      .setStreamPriority(new StreamPriority()
        .setDependency(requestStreamDependency)
        .setWeight(requestStreamWeight)
        .setExclusive(false));
    request.sendHead(h -> {
      request.setStreamPriority(new StreamPriority()
        .setDependency(requestStreamDependency2)
        .setWeight(requestStreamWeight2)
        .setExclusive(false));
      request.end();
    });
    await();
  }

  @Test
  public void testServerStreamPriorityNoChange() throws Exception {
    int dependency = 56;
    short weight = 43;
    boolean exclusive = true;
    waitFor(2);
    server.requestHandler(req -> {
      req.streamPriorityHandler(sp -> {
        fail("Stream priority handler should not be called " + sp);
      });
      assertEquals(weight, req.streamPriority().getWeight());
      assertEquals(dependency, req.streamPriority().getDependency());
      assertEquals(exclusive, req.streamPriority().isExclusive());
      req.response().end();
      req.endHandler(v -> {
        complete();
      });
    });
    startServer(testAddress);
    client = vertx.createHttpClient(createBaseClientOptions());
    HttpClientRequest request = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .onComplete( onSuccess(resp -> {
      resp.endHandler(v -> {
        complete();
      });
    }));
    request.setStreamPriority(new StreamPriority()
      .setDependency(dependency)
      .setWeight(weight)
      .setExclusive(exclusive));
    request.sendHead(h -> {
      request.setStreamPriority(new StreamPriority()
        .setDependency(dependency)
        .setWeight(weight)
        .setExclusive(exclusive));
      request.end();
    });
    await();
  }

  @Test
  public void testClientStreamPriorityNoChange() throws Exception {
    int dependency = 98;
    short weight = 55;
    boolean exclusive = false;
    waitFor(2);
    server.requestHandler(req -> {
      req.response().setStreamPriority(new StreamPriority()
        .setDependency(dependency)
        .setWeight(weight)
        .setExclusive(exclusive));
      req.response().write("hello");
      req.response().setStreamPriority(new StreamPriority()
        .setDependency(dependency)
        .setWeight(weight)
        .setExclusive(exclusive));
      req.response().end("world");
      req.endHandler(v -> {
        complete();
      });
    });
    startServer(testAddress);
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .onComplete(onSuccess(resp -> {
        assertEquals(weight, resp.request().getStreamPriority().getWeight());
        assertEquals(dependency, resp.request().getStreamPriority().getDependency());
        assertEquals(exclusive, resp.request().getStreamPriority().isExclusive());
        resp.streamPriorityHandler(sp -> {
          fail("Stream priority handler should not be called");
        });
        resp.endHandler(v -> {
          complete();
        });
      }))
      .end();
    await();
  }

  @Test
  public void testStreamWeightAndDependencyInheritance() throws Exception {
    int requestStreamDependency = 86;
    short requestStreamWeight = 53;
    waitFor(2);
    server.requestHandler(req -> {
      assertEquals(requestStreamWeight, req.streamPriority().getWeight());
      assertEquals(requestStreamDependency, req.streamPriority().getDependency());
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .onComplete(onSuccess(resp -> {
        assertEquals(requestStreamWeight, resp.request().getStreamPriority().getWeight());
        assertEquals(requestStreamDependency, resp.request().getStreamPriority().getDependency());
        complete();
      }))
      .setStreamPriority(new StreamPriority()
        .setDependency(requestStreamDependency)
        .setWeight(requestStreamWeight)
        .setExclusive(false))
      .end();
    await();
  }

  @Test
  public void testDefaultStreamWeightAndDependency() throws Exception {
    int defaultStreamDependency = 0;
    short defaultStreamWeight = Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
    waitFor(2);
    server.requestHandler(req -> {
        assertEquals(defaultStreamWeight, req.streamPriority().getWeight());
        assertEquals(defaultStreamDependency, req.streamPriority().getDependency());
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .onComplete(onSuccess(resp -> {
        assertEquals(defaultStreamWeight, resp.request().getStreamPriority().getWeight());
        assertEquals(defaultStreamDependency, resp.request().getStreamPriority().getDependency());
        complete();
      }))
      .end();
    await();
  }

  @Test
  public void testStreamWeightAndDependencyPushPromise() throws Exception {
    int pushStreamDependency = 456;
    short pushStreamWeight = 14;
    waitFor(4);
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/pushpath", ar -> {
        assertTrue(ar.succeeded());
        HttpServerResponse pushedResp = ar.result();
        pushedResp.setStreamPriority(new StreamPriority()
          .setDependency(pushStreamDependency)
          .setWeight(pushStreamWeight)
          .setExclusive(false));
        pushedResp.end();
      });
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .onComplete(resp -> {
        complete();
      }).pushHandler(pushReq -> {
      complete();
      pushReq.onComplete(onSuccess(pushResp -> {
        assertEquals(pushStreamDependency, pushResp.request().getStreamPriority().getDependency());
        assertEquals(pushStreamWeight, pushResp.request().getStreamPriority().getWeight());
        complete();
      }));
    })
      .end();
    await();
  }

  @Test
  public void testStreamWeightAndDependencyInheritancePushPromise() throws Exception {
    int reqStreamDependency = 556;
    short reqStreamWeight = 84;
    waitFor(4);
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/pushpath", ar -> {
        assertTrue(ar.succeeded());
        HttpServerResponse pushedResp = ar.result();
        pushedResp.end();
      });
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .onComplete(onSuccess(resp -> {
        complete();
      })).pushHandler(pushReq -> {
      complete();
      pushReq.onComplete(onSuccess(pushResp -> {
        assertEquals(reqStreamDependency, pushResp.request().getStreamPriority().getDependency());
        assertEquals(reqStreamWeight, pushResp.request().getStreamPriority().getWeight());
        complete();
      }));
    }).setStreamPriority(
      new StreamPriority()
        .setDependency(reqStreamDependency)
        .setWeight(reqStreamWeight)
        .setExclusive(false))
      .end();
    await();
  }

  @Test
  public void testClearTextUpgradeWithBody() throws Exception {
    server.close();
    server = vertx.createHttpServer().requestHandler(req -> {
      req.bodyHandler(body -> req.response().end(body));
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2));
    client.connectionHandler(conn -> {
      conn.goAwayHandler(ga -> {
        assertEquals(0, ga.getErrorCode());
      });
    });
    Buffer payload = Buffer.buffer("some-data");
    HttpClientRequest req = client.request(testAddress, new RequestOptions().setSsl(false));
    req.onComplete(onSuccess(response -> {
      response.body(onSuccess(body -> {
        assertEquals(Buffer.buffer().appendBuffer(payload).appendBuffer(payload), body);
        testComplete();
      }));
    }));
    req.putHeader("Content-Length", "" + payload.length() * 2);
    req.exceptionHandler(this::fail);
    req.write(payload);
    Thread.sleep(1000);
    req.end(payload);
    await();
  }

  @Test
  public void testClearTextUpgradeWithBodyTooLongFrameResponse() throws Exception {
    server.close();
    Buffer buffer = TestUtils.randomBuffer(1024);
    server = vertx.createHttpServer().requestHandler(req -> {
      req.response().setChunked(true);
      vertx.setPeriodic(1, id -> {
        req.response().write(buffer);
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2));
    HttpClientRequest req = client.request(testAddress, new RequestOptions().setSsl(false));
    req.onComplete(onFailure(err -> {}));
    req.setChunked(true);
    req.exceptionHandler(err -> {
      if (err instanceof TooLongFrameException) {
        testComplete();
      }
    });
    req.sendHead();
    await();
  }

  @Test
  public void testSslHandshakeTimeout() throws Exception {
    waitFor(2);
    HttpServerOptions opts = createBaseServerOptions()
      .setSslHandshakeTimeout(1234)
      .setSslHandshakeTimeoutUnit(TimeUnit.MILLISECONDS);
    server.close();
    server = vertx.createHttpServer(opts)
      .requestHandler(req -> fail("Should not be called"))
      .exceptionHandler(err -> {
        if (err instanceof SSLHandshakeException) {
          assertEquals("handshake timed out after 1234ms", err.getMessage());
          complete();
        }
      });
    startServer();
    vertx.createNetClient().connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST)
      .onFailure(this::fail)
      .onSuccess(so -> so.closeHandler(u -> complete()));
    await();
  }
}
