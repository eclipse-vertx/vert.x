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

import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DuplexChannel;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.Http2ServerConnection;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.TestUtils;
import io.vertx.test.tls.Cert;
import org.junit.Ignore;
import org.junit.Test;

import javax.net.ssl.SSLHandshakeException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
      client.request(requestOptions)
        .onComplete(onSuccess(req -> {
          req.send(onSuccess(resp -> {
            assertEquals(200, resp.statusCode());
            resp.handler(buff -> {
              assertEquals(Buffer.buffer("hello world"), buff);
              testComplete();
            });
          }));
        }));
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
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.send(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          resp.endHandler(v2 -> {
            testComplete();
          });
        }));
      }));
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
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.send(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          resp.endHandler(v2 -> {
            assertEquals(1, resp.trailers().size());
            assertEquals("trailer", resp.trailers().get("some"));
            testComplete();
          });
        }));
      }));
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
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req
          .response(onFailure(err -> {
            assertTrue(err instanceof StreamResetException);
            complete();
          }))
          .exceptionHandler(err -> {
            assertTrue(err instanceof StreamResetException);
            complete();
          })
          .sendHead();
      }));
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
    disableThreadChecks();
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
    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
        req.response(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          testComplete();
        }));
        req
          .setChunked(true)
          .sendHead();
        new Thread(() -> {
          try {
            awaitLatch(latch2); // The next write won't be buffered
          } catch (InterruptedException e) {
            fail(e);
            return;
          }
          req.write("hello ");
          req.end("world");
        }).start();
      }));
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
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      }));
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
    client.request(new RequestOptions(requestOptions).setURI("/0")).onComplete(onSuccess(req -> {
      req.send(onSuccess(resp -> {
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
      }));
    }));
    awaitLatch(fullLatch);
    client.request(new RequestOptions(requestOptions).setURI("/1")).onComplete(onSuccess(req -> {
      req.send(onSuccess(resp -> {
        resp.endHandler(v -> {
          complete();
        });
      }));
    }));
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
    Promise<Void> l = Promise.promise();
    client.request(new RequestOptions(requestOptions).setURI("/0")).onComplete(onSuccess(req1 -> {
      req1
        .response(resp -> {
          complete();
        })
        .setChunked(true)
        .sendHead(onSuccess(v -> {
          vertx.setPeriodic(1, id -> {
            req1.write(Buffer.buffer(TestUtils.randomAlphaString(512)));
            if (req1.writeQueueFull()) {
              req1.writeQueueFull();
              vertx.cancelTimer(id);
              l.complete();
            }
          });
      }));

      l.future().onComplete(onSuccess(v -> {
        client.request(new RequestOptions(requestOptions).setURI("/1")).onComplete(onSuccess(req2 -> {
          req2
            .response(onSuccess(resp -> {
              complete();
            }))
            .setChunked(true);
          assertFalse(req2.writeQueueFull());
          req2.sendHead();
          waitUntil(req2::writeQueueFull);
          resumeLatch.complete(null);
          waitUntil(() -> !req2.writeQueueFull(), () -> {
            req1.end();
            req2.end(buffer);
          });
        }));
      }));
    }));
    await();
  }

  private void waitUntil(BooleanSupplier pred, Runnable cont) {
    if (pred.getAsBoolean()) {
      cont.run();
    } else {
      vertx.setTimer(1, id -> {
        waitUntil(pred, cont);
      });
    }
  }

  @Test
  public void testResetClientRequestNotYetSent() throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setInitialSettings(new Http2Settings().setMaxConcurrentStreams(1)));
    server.requestHandler(req -> {
      fail();
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.response(onFailure(err -> {
        complete();
      }));
      assertTrue(req.reset());
    }));
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
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send(onFailure(err -> {}));
    }));
    AsyncTestBase.assertWaitUntil(() -> closed.get() == 1);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        testComplete();
      }));
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
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(HttpVersion.HTTP_1_1, resp.version());
        complete();
      }));
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
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(HttpVersion.HTTP_1_1, resp.version());
        complete();
      }));
    await();
  }

  @Test
  public void testClientMakeRequestHttp2WithSSLWithoutAlpn() throws Exception {
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setUseAlpn(false));
    try {
      client.request(requestOptions);
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
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.send(onSuccess(resp -> complete()));
      }));
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
    client.request(new RequestOptions(requestOptions).setTimeout(10000))
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> complete()));
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
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertNull(resp.getHeader("content-length"));
        resp.bodyHandler(body -> {
          assertEquals("HelloWorld", body.toString());
          complete();
        });
      }));
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
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .setStreamPriority(new StreamPriority()
          .setDependency(requestStreamDependency)
          .setWeight(requestStreamWeight)
          .setExclusive(false))
        .send(onSuccess(resp -> {
          assertEquals(responseStreamWeight, resp.request().getStreamPriority().getWeight());
          assertEquals(responseStreamDependency, resp.request().getStreamPriority().getDependency());
          complete();
        }));
    }));
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
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .response(onSuccess(resp -> {
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
      req.sendHead(h -> {
        req.setStreamPriority(new StreamPriority()
          .setDependency(requestStreamDependency2)
          .setWeight(requestStreamWeight2)
          .setExclusive(false));
        req.end();
      });
    }));
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
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .response( onSuccess(resp -> {
          resp.endHandler(v -> {
            complete();
          });
        }));
      req.setStreamPriority(new StreamPriority()
        .setDependency(dependency)
        .setWeight(weight)
        .setExclusive(exclusive));
      req.sendHead(h -> {
        req.setStreamPriority(new StreamPriority()
          .setDependency(dependency)
          .setWeight(weight)
          .setExclusive(exclusive));
        req.end();
      });
    }));
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
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send(onSuccess(resp -> {
        assertEquals(weight, resp.request().getStreamPriority().getWeight());
        assertEquals(dependency, resp.request().getStreamPriority().getDependency());
        assertEquals(exclusive, resp.request().getStreamPriority().isExclusive());
        resp.streamPriorityHandler(sp -> {
          fail("Stream priority handler should not be called");
        });
        resp.endHandler(v -> {
          complete();
        });
      }));
    }));
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
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .setStreamPriority(new StreamPriority()
          .setDependency(requestStreamDependency)
          .setWeight(requestStreamWeight)
          .setExclusive(false))
        .send(onSuccess(resp -> {
          assertEquals(requestStreamWeight, resp.request().getStreamPriority().getWeight());
          assertEquals(requestStreamDependency, resp.request().getStreamPriority().getDependency());
          complete();
        }));
    }));
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
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send(onSuccess(resp -> {
        assertEquals(defaultStreamWeight, req.getStreamPriority().getWeight());
        assertEquals(defaultStreamDependency, req.getStreamPriority().getDependency());
        complete();
      }));
    }));
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
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .pushHandler(pushReq -> {
        complete();
        pushReq.response(onSuccess(pushResp -> {
          assertEquals(pushStreamDependency, pushResp.request().getStreamPriority().getDependency());
          assertEquals(pushStreamWeight, pushResp.request().getStreamPriority().getWeight());
          complete();
        }));
      })
        .send(onSuccess(resp -> {
          complete();
        }));
    }));
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
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .pushHandler(pushReq -> {
        complete();
        pushReq.response(onSuccess(pushResp -> {
          assertEquals(reqStreamDependency, pushResp.request().getStreamPriority().getDependency());
          assertEquals(reqStreamWeight, pushResp.request().getStreamPriority().getWeight());
          complete();
        }));
      }).setStreamPriority(
        new StreamPriority()
          .setDependency(reqStreamDependency)
          .setWeight(reqStreamWeight)
          .setExclusive(false))
        .send(onSuccess(resp -> {
          complete();
        }));
    }));
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
    client.request(new RequestOptions(requestOptions).setSsl(false)).onComplete(onSuccess(req -> {
      req.response(onSuccess(response -> {
        response.body(onSuccess(body -> {
          assertEquals(Buffer.buffer().appendBuffer(payload).appendBuffer(payload), body);
          testComplete();
        }));
      }));
      req.putHeader("Content-Length", "" + payload.length() * 2);
      req.exceptionHandler(this::fail);
      req.write(payload);
      vertx.setTimer(1000, id -> {
        req.end(payload);
      });
    }));
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
    client.request(new RequestOptions(requestOptions).setSsl(false)).onComplete(onSuccess(req -> {
      req.response(onFailure(err -> {}));
      req.setChunked(true);
      req.exceptionHandler(err -> {
        if (err instanceof TooLongFrameException) {
          testComplete();
        }
      });
      req.sendHead();
    }));
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

  @Test
  public void testAppendToHttpChunks() throws Exception {
    List<String> expected = Arrays.asList("chunk-1", "chunk-2", "chunk-3");
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      expected.forEach(resp::write);
      resp.end(); // Will end an empty chunk
    });
    startServer(testAddress);
    client.request(requestOptions, onSuccess(req -> {
      req.send(onSuccess(resp -> {
        List<String> chunks = new ArrayList<>();
        resp.handler(chunk -> {
          chunk.appendString("-suffix");
          chunks.add(chunk.toString());
        });
        resp.endHandler(v -> {
          assertEquals(Stream.concat(expected.stream(), Stream.of(""))
            .map(s -> s + "-suffix")
            .collect(Collectors.toList()), chunks);
          testComplete();
        });
      }));
    }));
    await();
  }

  @Test
  public void testNonUpgradedH2CConnectionIsEvictedFromThePool() {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2));
    server.close();
    System.setProperty("vertx.disableH2c", "true");
    server = vertx.createHttpServer();
    try {
      server.requestHandler(req -> req.response().end());
      server.listen(testAddress, onSuccess(s -> {
        Promise<Void> promise = Promise.promise();
        client.request(requestOptions).compose(req -> {
          req.connection().closeHandler(v -> {
            promise.complete();
          });
          return req.send().compose(HttpClientResponse::body);
        }).onSuccess(b -> {
          server.close()
            .compose(r -> promise.future())
            .compose(a -> server.listen(testAddress)).onComplete(onSuccess(v -> {
            client.request(requestOptions).compose(req -> req.send().compose(HttpClientResponse::body)).onSuccess(b2 -> {
              testComplete();
            });
          }));
        });
      }));
      await();
    } finally {
      System.clearProperty("vertx.disableH2c");
    }
  }

  /**
   * Test that socket close (without an HTTP/2 go away frame) removes the connection from the pool
   * before the streams are notified. Otherwise a notified stream might reuse a stale connection from
   * the pool.
   */
  @Test
  public void testConnectionCloseEvictsConnectionFromThePoolBeforeStreamsAreClosed() throws Exception {
    Set<HttpConnection> serverConnections = new HashSet<>();
    server.requestHandler(req -> {
      serverConnections.add(req.connection());
      switch (req.path()) {
        case "/1":
          req.response().end();
          break;
        case "/2":
          assertEquals(1, serverConnections.size());
          // Socket close without HTTP/2 go away
          Channel ch = ((ConnectionBase) req.connection()).channel();
          ChannelPromise promise = ch.newPromise();
          ch.unsafe().close(promise);
          break;
        case "/3":
          assertEquals(2, serverConnections.size());
          req.response().end();
          break;
      }
    });
    startServer(testAddress);
    Future<Buffer> f1 = client.request(new RequestOptions(requestOptions).setURI("/1"))
      .compose(req -> req.send()
        .compose(HttpClientResponse::body));
    f1.onComplete(onSuccess(v -> {
      Future<Buffer> f2 = client.request(new RequestOptions(requestOptions).setURI("/2"))
        .compose(req -> {
          System.out.println(req.connection());
          return req.send()
            .compose(HttpClientResponse::body);
        });
      f2.onComplete(onFailure(v2 -> {
        Future<Buffer> f3 = client.request(new RequestOptions(requestOptions).setURI("/3"))
          .compose(req -> {
            System.out.println(req.connection());
            return req.send()
              .compose(HttpClientResponse::body);
          });
        f3.onComplete(onSuccess(vvv -> {
          testComplete();
        }));
      }));
    }));
    await();
  }
}
