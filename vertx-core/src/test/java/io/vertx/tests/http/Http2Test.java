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

package io.vertx.tests.http;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.TestUtils;
import io.vertx.test.tls.Cert;
import org.junit.Ignore;
import org.junit.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.vertx.test.core.AssertExpectations.that;

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
  public void testServerResponseWriteBufferFromOtherThread() throws Exception {
    server.requestHandler(req -> {
      runAsync(() -> {
        req.response().end("hello world");
      });
    });
    startServer(testAddress);
    client.request(requestOptions).compose(req -> req
        .send()
      .expecting(that(resp -> assertEquals(200, resp.statusCode())))
      .compose(HttpClientResponse::body)).
      onComplete(onSuccess(body -> {
        assertEquals(Buffer.buffer("hello world"), body);
        testComplete();
      }));
    await();
  }

  @Test
  public void testServerResponseEndFromOtherThread() throws Exception {
    server.requestHandler(req -> {
      runAsync(() -> {
        req.response().end();
      });
    });
    startServer(testAddress);
    client.request(requestOptions).compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testServerResponseEndWithTrailersFromOtherThread() throws Exception {
    server.requestHandler(req -> {
      runAsync(() -> {
        req.response().putTrailer("some", "trailer").end();
      });
    });
    startServer(testAddress);
    client.request(requestOptions).compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(resp -> resp.end().expecting(that(v -> {
          assertEquals(1, resp.trailers().size());
          assertEquals("trailer", resp.trailers().get("some"));
        }))))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testServerResponseResetFromOtherThread() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      runAsync(() -> {
        req.response().reset(0);
      });
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .response().onComplete(onFailure(err -> {
          assertTrue(err instanceof StreamResetException);
          complete();
        }));
      req.exceptionHandler(err -> {
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
    disableThreadChecks();
    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(1);
    server.requestHandler(req -> {
      latch2.countDown();
      req.endHandler(v -> {
        req.response().end();
      });
    });
    startServer(testAddress);
    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
        req.response().onComplete(onSuccess(resp -> {
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
      .setKeyCertOptions(Cert.SERVER_PEM.get())
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
  public void testResetClientRequestNotYetSent() throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setInitialSettings(new Http2Settings().setMaxConcurrentStreams(1)));
    server.requestHandler(req -> {
      fail();
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.response().onComplete(onFailure(err -> complete()));
      assertTrue(req.reset());
    }));
    await();
  }

  @Test
  public void testDiscardConnectionWhenChannelBecomesInactive() throws Exception {
    AtomicInteger count = new AtomicInteger();
    server.requestHandler(req -> {
      if (count.getAndIncrement() == 0) {
        req.connection().close();
      } else {
        req.response().end();
      }
    });
    startServer(testAddress);
    AtomicInteger closed = new AtomicInteger();
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions())
      .withConnectHandler(conn -> conn.closeHandler(v -> closed.incrementAndGet()))
      .build();
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onFailure(err -> {}));
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
    client.request(requestOptions).onComplete(onFailure(err -> testComplete()));
    await();
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
        req.send().onComplete(onSuccess(resp -> complete()));
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
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions())
      .withConnectHandler(conn -> {
        assertEquals(0, conn.remoteSettings().getMaxConcurrentStreams());
        conn.remoteSettingsHandler(settings -> {
          assertEquals(10, conn.remoteSettings().getMaxConcurrentStreams());
          complete();
        });
      })
      .build();
    client.request(new RequestOptions(requestOptions).setTimeout(10000))
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> complete()));
    await();
  }

  @Test
  public void testMaxHaderListSize() throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setInitialSettings(new Http2Settings().setMaxHeaderListSize(Integer.MAX_VALUE)));
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setTimeout(10000))
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(Integer.MAX_VALUE, resp.request().connection().remoteSettings().getMaxHeaderListSize());
        testComplete();
      }));
    await();
  }

  @Test
  public void testContentLengthNotRequired() throws Exception {
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
      .compose(req -> req.send().compose(resp -> {
        assertNull(resp.getHeader("content-length"));
        return resp.body();
      }))
      .onComplete(onSuccess(body -> {
        assertEquals("HelloWorld", body.toString());
        complete();
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
      req.response().setStreamPriority(new Http2StreamPriority()
        .setDependency(responseStreamDependency)
        .setWeight(responseStreamWeight)
        .setExclusive(false));
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .setStreamPriority(new Http2StreamPriority()
          .setDependency(requestStreamDependency)
          .setWeight(requestStreamWeight)
          .setExclusive(false))
        .send().onComplete(onSuccess(resp -> {
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
      req.response().setStreamPriority(new Http2StreamPriority()
        .setDependency(responseStreamDependency)
        .setWeight(responseStreamWeight)
        .setExclusive(false));
      req.response().write("hello");
      req.response().setStreamPriority(new Http2StreamPriority()
        .setDependency(responseStreamDependency2)
        .setWeight(responseStreamWeight2)
        .setExclusive(false));
      req.response().drainHandler(h -> {});
      req.response().end("world");
      complete();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .setStreamPriority(new Http2StreamPriority()
          .setDependency(requestStreamDependency)
          .setWeight(requestStreamWeight)
          .setExclusive(false))
        .response()
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
        }));
      req
        .sendHead()
        .onComplete(h -> {
          req.setStreamPriority(new Http2StreamPriority()
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
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .response().onComplete(onSuccess(resp -> {
          resp.endHandler(v -> {
            complete();
          });
        }));
      req.setStreamPriority(new Http2StreamPriority()
        .setDependency(dependency)
        .setWeight(weight)
        .setExclusive(exclusive));
      req
        .sendHead()
        .onComplete(h -> {
        req.setStreamPriority(new Http2StreamPriority()
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
      req.response().setStreamPriority(new Http2StreamPriority()
        .setDependency(dependency)
        .setWeight(weight)
        .setExclusive(exclusive));
      req.response().write("hello");
      req.response().setStreamPriority(new Http2StreamPriority()
        .setDependency(dependency)
        .setWeight(weight)
        .setExclusive(exclusive));
      req.response().end("world");
      req.endHandler(v -> {
        complete();
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .send()
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
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .setStreamPriority(new Http2StreamPriority()
          .setDependency(requestStreamDependency)
          .setWeight(requestStreamWeight)
          .setExclusive(false))
        .send()
        .onComplete(onSuccess(resp -> {
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
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
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
      req.response().push(HttpMethod.GET, "/pushpath").onComplete(onSuccess(pushedResp -> {
        pushedResp.setStreamPriority(new Http2StreamPriority()
          .setDependency(pushStreamDependency)
          .setWeight(pushStreamWeight)
          .setExclusive(false));
        pushedResp.end();
      }));
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .pushHandler(pushReq -> {
        complete();
        pushReq.response().onComplete(onSuccess(pushResp -> {
          assertEquals(pushStreamDependency, pushResp.request().getStreamPriority().getDependency());
          assertEquals(pushStreamWeight, pushResp.request().getStreamPriority().getWeight());
          complete();
        }));
      })
        .send().onComplete(onSuccess(resp -> {
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
      req.response().push(HttpMethod.GET, "/pushpath").onComplete(onSuccess(HttpServerResponse::end));
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .pushHandler(pushReq -> {
          complete();
          pushReq.response().onComplete(onSuccess(pushResp -> {
            assertEquals(reqStreamDependency, pushResp.request().getStreamPriority().getDependency());
            assertEquals(reqStreamWeight, pushResp.request().getStreamPriority().getWeight());
            complete();
          }));
        }).setStreamPriority(
          new Http2StreamPriority()
            .setDependency(reqStreamDependency)
            .setWeight(reqStreamWeight)
            .setExclusive(false))
        .send()
        .onComplete(onSuccess(resp -> {
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
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2))
      .withConnectHandler(conn -> {
        conn.goAwayHandler(ga -> {
          assertEquals(0, ga.getErrorCode());
        });
      })
      .build();
    Buffer payload = Buffer.buffer("some-data");
    client.request(new RequestOptions(requestOptions).setSsl(false)).onComplete(onSuccess(req -> {
      req.response()
        .compose(HttpClientResponse::body)
        .onComplete(onSuccess(body -> {
          assertEquals(Buffer.buffer().appendBuffer(payload).appendBuffer(payload), body);
          testComplete();
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
      req.response().onComplete(onFailure(err -> {}));
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

  @Ignore
  @Test
  public void testAppendToHttpChunks() throws Exception {
    List<String> expected = Arrays.asList("chunk-1", "chunk-2", "chunk-3");
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      expected.forEach(resp::write);
      resp.end(); // Will end an empty chunk
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
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
  public void testNonUpgradedH2CConnectionIsEvictedFromThePool() throws Exception {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2));
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions().setHttp2ClearTextEnabled(false));
    Promise<Void> p = Promise.promise();
    AtomicBoolean first = new AtomicBoolean(true);
    server.requestHandler(req -> {
      if (first.compareAndSet(true, false)) {
        HttpConnection conn = req.connection();
        p.future().onComplete(ar -> {
          conn.close();
        });
      }
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions).compose(req1 -> {
      req1.connection().closeHandler(v1 -> {
        vertx.runOnContext(v2 -> {
          client.request(requestOptions).compose(req2 -> req2.send().compose(HttpClientResponse::body)).onComplete(onSuccess(b2 -> {
            testComplete();
          }));
        });
      });
      return req1.send().compose(resp -> {
        assertEquals(HttpVersion.HTTP_1_1, resp.version());
        return resp.body();
      });
    }).onComplete(onSuccess(b -> {
      p.complete();
    }));
    await();
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
        .compose(req -> req.send()
          .compose(HttpClientResponse::body));
      f2.onComplete(onFailure(v2 -> {
        Future<Buffer> f3 = client.request(new RequestOptions(requestOptions).setURI("/3"))
          .compose(req -> req.send()
            .compose(HttpClientResponse::body));
        f3.onComplete(onSuccess(vvv -> {
          testComplete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testRstFloodProtection() throws Exception {
    server.requestHandler(req -> {
    });
    startServer(testAddress);
    int num = HttpServerOptions.DEFAULT_HTTP2_RST_FLOOD_MAX_RST_FRAME_PER_WINDOW + 1;
    for (int i = 0;i < num;i++) {
      int val = i;
      client.request(requestOptions).onComplete(onSuccess(req -> {
        if (val == 0) {
          req
            .connection()
            .goAwayHandler(ga -> {
              assertEquals(11, ga.getErrorCode()); // Enhance your calm
              testComplete();
            });
        }
        req.end().onComplete(onSuccess(v -> {
          req.reset();
        }));
      }));
    }
    await();
  }

  @Test
  public void testStreamResetErrorMapping() throws Exception {
    server.requestHandler(req -> {
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.exceptionHandler(err -> {
        assertTrue(err instanceof StreamResetException);
        StreamResetException sre = (StreamResetException) err;
        assertEquals(10, sre.getCode());
        testComplete();
      });
      // Force stream allocation
      req.sendHead().onComplete(onSuccess(v -> {
        req.reset(10);
      }));
    }));
    await();
  }

  @Test
  public void testUnsupportedAlpnVersion() throws Exception {
    testUnsupportedAlpnVersion(new JdkSSLEngineOptions(), false);
  }

  @Test
  public void testUnsupportedAlpnVersionOpenSSL() throws Exception {
    testUnsupportedAlpnVersion(new OpenSSLEngineOptions(), true);
  }

  private void testUnsupportedAlpnVersion(SSLEngineOptions engine, boolean accept) throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions()
      .setSslEngineOptions(engine)
      .setAlpnVersions(Collections.singletonList(HttpVersion.HTTP_2))
    );
    server.requestHandler(request -> {
      request.response().end();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1));
    client.request(requestOptions).onComplete(ar -> {
      if (ar.succeeded()) {
        if (accept) {
          ar.result().send().onComplete(onSuccess(resp -> {
            testComplete();
          }));
        } else {
          fail();
        }
      } else {
        if (accept) {
          fail();
        } else {
          testComplete();
        }
      }
    });
    await();
  }

  @Test
  public void testSendFileCancellation() throws Exception {

    Path webroot = Files.createTempDirectory("webroot");
    File res = new File(webroot.toFile(), "large.dat");
    RandomAccessFile f = new RandomAccessFile(res, "rw");
    f.setLength(1024 * 1024);

    AtomicInteger errors = new AtomicInteger();
    vertx.getOrCreateContext().exceptionHandler(err -> {
      errors.incrementAndGet();
    });

    server.requestHandler(request -> {
      request
        .response()
        .sendFile(res.getAbsolutePath())
        .onComplete(onFailure(ar -> {
          assertEquals(0, errors.get());
          testComplete();
        }));
    });

    startServer();

    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
        req.send().onComplete(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          assertEquals(HttpVersion.HTTP_2, resp.version());
          req.connection().close();
        }));
      }));

    await();
  }
}
