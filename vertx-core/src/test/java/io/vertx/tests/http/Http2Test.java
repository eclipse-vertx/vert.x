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
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpConfig;
import io.vertx.test.tls.Cert;
import org.junit.Assert;
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

import static io.vertx.test.core.AssertExpectations.that;
import static io.vertx.test.core.TestUtils.onSuccess;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2Test extends HttpTest {

  public Http2Test() {
    this(false);
  }

  protected Http2Test(boolean multiplex) {
    super(new HttpConfig.H2(multiplex));
  }

  @Test
  public void testCloseHandlerNotCalledWhenConnectionClosedAfterEnd(Checkpoint checkpoint) throws Exception {
    testCloseHandlerNotCalledWhenConnectionClosedAfterEnd(checkpoint, 1);
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
    Buffer body = client.request(requestOptions).compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .await();
    assertEquals(Buffer.buffer("hello world"), body);
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
      .await();
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
      .await();
  }

  @Test
  public void testServerResponseResetFromOtherThread(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    server.requestHandler(req -> {
      runAsync(() -> {
        req.response().reset(0);
      });
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .response().onComplete(TestUtils.onFailure(err -> {
          assertTrue(err instanceof StreamResetException);
          checkpoint1.succeed();
        }));
      req.exceptionHandler(err -> {
          assertTrue(err instanceof StreamResetException);
          checkpoint2.succeed();
        })
        .writeHead();
    }));
  }

  void runAsync(Runnable runnable) {
    new Thread(() -> {
      try {
        runnable.run();
      } catch (Exception e) {
        Assert.fail(e.getMessage());
      }
    }).start();
  }

  @Test
  public void testClientRequestWriteFromOtherThread(Checkpoint checkpoint) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      latch.countDown();
      req.endHandler(v -> {
        req.response().end();
      });
    });
    startServer(testAddress);
    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
        req.response()
          .expecting(HttpResponseExpectation.SC_OK)
          .onComplete(checkpoint);
        req
          .setChunked(true)
          .writeHead();
        new Thread(() -> {
          try {
            latch.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
            return;
          }
          req.write("hello ");
          req.end("world");
        }).start();
      }));
  }

  @Test
  public void testServerOpenSSL() throws Exception {
    HttpServerOptions opts = Http2TestBase.createHttp2ServerOptions()
      .setKeyCertOptions(Cert.SERVER_PEM.get())
      .setSslEngineOptions(new OpenSSLEngineOptions());
    client = vertx.createHttpClient(Http2TestBase.createHttp2ClientOptions());
    server = vertx.createHttpServer(opts);
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end))
      .await();
  }

  @Test
  public void testResetClientRequestNotYetSent(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    server = vertx.createHttpServer(Http2TestBase.createHttp2ServerOptions().setInitialSettings(new Http2Settings().setMaxConcurrentStreams(1)));
    server.requestHandler(req -> {
      Assert.fail();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
        req
          .response()
          .onComplete(TestUtils.onFailure(err -> checkpoint1.succeed()));
        req
          .reset()
          .onComplete(checkpoint2);
      }));
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
    client = vertx.httpClientBuilder()
      .with(Http2TestBase.createHttp2ClientOptions())
      .withConnectHandler(conn -> conn.closeHandler(v -> closed.incrementAndGet()))
      .build();
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(TestUtils.onFailure(err -> {}));
    }));
    AsyncTestBase.assertWaitUntil(() -> closed.get() == 1);
    client.request(requestOptions)
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::end))
      .await();
  }

  @Test
  public void testClientDoesNotSupportAlpn() throws Exception {
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      req.response().end();
    });
    startServer(testAddress);
    client = vertx.createHttpClient(Http2TestBase.createHttp2ClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1).setUseAlpn(false));
    HttpVersion version = client.request(requestOptions)
      .compose(request -> request
        .send()
        .compose(response -> response
          .end()
          .map(response.version())))
      .await();
    assertEquals(HttpVersion.HTTP_1_1, version);
  }

  @Test
  public void testServerDoesNotSupportAlpn() throws Exception {
    server = vertx.createHttpServer(Http2TestBase.createHttp2ServerOptions().setUseAlpn(false));
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      req.response().end();
    });
    startServer(testAddress);
    HttpVersion version = client.request(requestOptions)
      .compose(request -> request
        .send()
        .compose(response -> response
          .end()
          .map(response.version())))
      .await();
    assertEquals(HttpVersion.HTTP_1_1, version);
  }

  @Test
  public void testClientMakeRequestHttp2WithSSLWithoutAlpn() throws Exception {
    client = vertx.createHttpClient(Http2TestBase.createHttp2ClientOptions().setUseAlpn(false));
    assertThatThrownBy(() -> {
      client.request(requestOptions).await();
    });
  }

  @Test
  public void testServePendingRequests(Checkpoint checkpoint) throws Exception {
    int n = 10;
    CountDownLatch latch = checkpoint.asLatch(n);
    LinkedList<HttpServerRequest> requests = new LinkedList<>();
    Set<HttpConnection> connections = new HashSet<>();
    server.requestHandler(req -> {
      requests.add(req);
      connections.add(req.connection());
      assertEquals(1, connections.size());
      if (requests.size() == n) {
        while (!requests.isEmpty()) {
          requests.removeFirst().response().end();
        }
      }
    });
    startServer(testAddress);
    for (int i = 0;i < n;i++) {
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.send().onComplete(onSuccess(resp -> latch.countDown()));
      }));
    }
  }

  @Test
  public void testInitialMaxConcurrentStreamZero(Checkpoint checkpoint) throws Exception {
    server = vertx.createHttpServer(Http2TestBase.createHttp2ServerOptions().setInitialSettings(new Http2Settings().setMaxConcurrentStreams(0)));
    server.requestHandler(req -> {
      req.response().end();
    });
    server.connectionHandler(conn -> {
      vertx.setTimer(500, id -> {
        conn.updateSettings(new Http2Settings().setMaxConcurrentStreams(10));
      });
    });
    startServer(testAddress);
    client = vertx.httpClientBuilder()
      .with(Http2TestBase.createHttp2ClientOptions())
      .withConnectHandler(conn -> {
        assertEquals(0L, (long)conn.remoteSettings().get(Http2Settings.MAX_CONCURRENT_STREAMS));
        conn.remoteSettingsHandler(settings -> {
          assertEquals(10L, (long)conn.remoteSettings().get(Http2Settings.MAX_CONCURRENT_STREAMS));
          checkpoint.succeed();
        });
      })
      .build();
    client.request(new RequestOptions(requestOptions).setTimeout(10000))
      .compose(HttpClientRequest::send)
      .await();
  }

  @Test
  public void testMaxHaderListSize() throws Exception {
    server = vertx.createHttpServer(Http2TestBase.createHttp2ServerOptions().setInitialSettings(new Http2Settings().setMaxHeaderListSize(Integer.MAX_VALUE)));
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    Long value = client.request(new RequestOptions(requestOptions).setTimeout(10000))
      .compose(request -> request
        .send()
        .map(response -> request
          .connection()
          .remoteSettings()
          .get(Http2Settings.MAX_HEADER_LIST_SIZE)))
      .await();
    assertEquals(Integer.MAX_VALUE, (long)value);
  }

  @Test
  public void testContentLengthNotRequired(Checkpoint checkpoint) throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.write("Hello");
      resp.end("World");
      assertNull(resp.headers().get("content-length"));
      checkpoint.succeed();
    });
    startServer(testAddress);
    Buffer body = client.request(requestOptions)
      .compose(req -> req.send().compose(resp -> {
        assertNull(resp.getHeader("content-length"));
        return resp.body();
      }))
      .await();
    assertEquals("HelloWorld", body.toString());
  }

  @Test
  public void testStreamWeightAndDependency(Checkpoint checkpoint) throws Exception {
    int requestStreamDependency = 56;
    short requestStreamWeight = 43;
    int responseStreamDependency = 98;
    short responseStreamWeight = 55;
    server.requestHandler(req -> {
      assertEquals(requestStreamWeight, req.streamPriority().getWeight());
      assertEquals(requestStreamDependency, req.streamPriority().getDependency());
      req.response().setStreamPriority(new StreamPriority()
        .setDependency(responseStreamDependency)
        .setWeight(responseStreamWeight)
        .setExclusive(false));
      req
        .response()
        .end()
        .onComplete(checkpoint);
    });
    startServer(testAddress);
    client = vertx.createHttpClient(Http2TestBase.createHttp2ClientOptions());
    client.request(requestOptions).compose(req -> req
        .setStreamPriority(new StreamPriority()
          .setDependency(requestStreamDependency)
          .setWeight(requestStreamWeight)
          .setExclusive(false))
        .send()
        .compose(resp -> {
          assertEquals(responseStreamWeight, resp.request().getStreamPriority().getWeight());
          assertEquals(responseStreamDependency, resp.request().getStreamPriority().getDependency());
          return resp.end();
        }))
      .await();
  }

  @Test
  public void testStreamWeightAndDependencyChange(Checkpoint checkpoint1, Checkpoint checkpoint2, Checkpoint checkpoint3, Checkpoint checkpoint4) throws Exception {
    int requestStreamDependency = 56;
    short requestStreamWeight = 43;
    int requestStreamDependency2 = 157;
    short requestStreamWeight2 = 143;
    int responseStreamDependency = 98;
    short responseStreamWeight = 55;
    int responseStreamDependency2 = 198;
    short responseStreamWeight2 = 155;
    server.requestHandler(req -> {
      req.streamPriorityHandler( sp -> {
        assertEquals(requestStreamWeight2, sp.getWeight());
        assertEquals(requestStreamDependency2, sp.getDependency());
        assertEquals(requestStreamWeight2, req.streamPriority().getWeight());
        assertEquals(requestStreamDependency2, req.streamPriority().getDependency());
        checkpoint1.succeed();
      });
      assertEquals(requestStreamWeight, req.streamPriority().getWeight());
      assertEquals(requestStreamDependency, req.streamPriority().getDependency());
      HttpServerResponse response = req.response();
      response.setStreamPriority(new StreamPriority()
        .setDependency(responseStreamDependency)
        .setWeight(responseStreamWeight)
        .setExclusive(false));
      response.write("hello");
      response.setStreamPriority(new StreamPriority()
        .setDependency(responseStreamDependency2)
        .setWeight(responseStreamWeight2)
        .setExclusive(false));
      response.drainHandler(h -> {});
      response
        .end("world")
        .onComplete(checkpoint2);
    });
    startServer(testAddress);
    client = vertx.createHttpClient(Http2TestBase.createHttp2ClientOptions());
    client.request(requestOptions).compose(req -> {
      req
        .setStreamPriority(new StreamPriority()
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
            checkpoint3.succeed();
          });
          checkpoint4.succeed();
        }));
      return req
        .writeHead()
        .compose(v -> req
          .setStreamPriority(new StreamPriority()
            .setDependency(requestStreamDependency2)
            .setWeight(requestStreamWeight2)
            .setExclusive(false))
          .end());
    }).await();
  }

  @Test
  public void testServerStreamPriorityNoChange(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    int dependency = 56;
    short weight = 43;
    boolean exclusive = true;
    server.requestHandler(req -> {
      req.streamPriorityHandler(sp -> {
        Assert.fail("Stream priority handler should not be called " + sp);
      });
      assertEquals(weight, req.streamPriority().getWeight());
      assertEquals(dependency, req.streamPriority().getDependency());
      assertEquals(exclusive, req.streamPriority().isExclusive());
      req.response().end();
      req.endHandler(v -> {
        checkpoint1.succeed();
      });
    });
    startServer(testAddress);
    client = vertx.createHttpClient(Http2TestBase.createHttp2ClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .response().onComplete(onSuccess(resp -> {
          resp.endHandler(v -> checkpoint2.succeed());
        }));
      req.setStreamPriority(new StreamPriority()
        .setDependency(dependency)
        .setWeight(weight)
        .setExclusive(exclusive));
      req
        .writeHead()
        .onComplete(h -> {
        req.setStreamPriority(new StreamPriority()
          .setDependency(dependency)
          .setWeight(weight)
          .setExclusive(exclusive));
        req.end();
      });
    }));
  }

  @Test
  public void testClientStreamPriorityNoChange(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    int dependency = 98;
    short weight = 55;
    boolean exclusive = false;
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
      req.endHandler(v -> checkpoint1.succeed());
    });
    startServer(testAddress);
    client = vertx.createHttpClient(Http2TestBase.createHttp2ClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .send()
        .onComplete(onSuccess(resp -> {
          assertEquals(weight, resp.request().getStreamPriority().getWeight());
          assertEquals(dependency, resp.request().getStreamPriority().getDependency());
          assertEquals(exclusive, resp.request().getStreamPriority().isExclusive());
          resp.streamPriorityHandler(sp -> {
            Assert.fail("Stream priority handler should not be called");
          });
          resp.endHandler(v -> checkpoint2.succeed());
        }));
    }));
  }

  @Test
  public void testStreamWeightAndDependencyInheritance() throws Exception {
    int requestStreamDependency = 86;
    short requestStreamWeight = 53;
    server.requestHandler(req -> {
      assertEquals(requestStreamWeight, req.streamPriority().getWeight());
      assertEquals(requestStreamDependency, req.streamPriority().getDependency());
      req.response().end();
    });
    startServer(testAddress);
    client = vertx.createHttpClient(Http2TestBase.createHttp2ClientOptions());
    client.request(requestOptions).compose(req -> req
      .setStreamPriority(new StreamPriority()
        .setDependency(requestStreamDependency)
        .setWeight(requestStreamWeight)
        .setExclusive(false))
      .send()
      .compose(resp -> {
        assertEquals(requestStreamWeight, resp.request().getStreamPriority().getWeight());
        assertEquals(requestStreamDependency, resp.request().getStreamPriority().getDependency());
        return resp.end();
      }))
      .await();
  }

  @Test
  public void testDefaultStreamWeightAndDependency() throws Exception {
    int defaultStreamDependency = 0;
    short defaultStreamWeight = Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
    server.requestHandler(req -> {
      assertEquals(defaultStreamWeight, req.streamPriority().getWeight());
      assertEquals(defaultStreamDependency, req.streamPriority().getDependency());
      req.response().end();
    });
    startServer(testAddress);
    client = vertx.createHttpClient(Http2TestBase.createHttp2ClientOptions());
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .compose(resp -> {
          assertEquals(defaultStreamWeight, req.getStreamPriority().getWeight());
          assertEquals(defaultStreamDependency, req.getStreamPriority().getDependency());
          return resp.end();
        }))
      .await();
  }

  @Test
  public void testStreamWeightAndDependencyPushPromise(Checkpoint checkpoint) throws Exception {
    int pushStreamDependency = 456;
    short pushStreamWeight = 14;
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/pushpath").onComplete(onSuccess(pushedResp -> {
        pushedResp.setStreamPriority(new StreamPriority()
          .setDependency(pushStreamDependency)
          .setWeight(pushStreamWeight)
          .setExclusive(false));
        pushedResp.end();
      }));
      req.response().end();
    });
    startServer(testAddress);
    client = vertx.createHttpClient(Http2TestBase.createHttp2ClientOptions());
    client.request(requestOptions).compose(req ->
      req
        .pushHandler(pushReq -> {
          pushReq
            .response()
            .onComplete(onSuccess(pushResp -> {
              assertEquals(pushStreamDependency, pushResp.request().getStreamPriority().getDependency());
              assertEquals(pushStreamWeight, pushResp.request().getStreamPriority().getWeight());
              checkpoint.succeed();
            }));
        })
        .send()
        .compose(HttpClientResponse::end)
    ).await();
  }

  @Test
  public void testStreamWeightAndDependencyInheritancePushPromise(Checkpoint checkpoint) throws Exception {
    int reqStreamDependency = 556;
    short reqStreamWeight = 84;
    server.requestHandler(req -> {
      HttpServerResponse response = req.response();
      response.push(HttpMethod.GET, "/pushpath").onComplete(onSuccess(HttpServerResponse::end));
      response.end();
    });
    startServer(testAddress);
    client = vertx.createHttpClient(Http2TestBase.createHttp2ClientOptions());
    client
      .request(requestOptions)
      .compose(req ->
        req
          .pushHandler(pushReq -> {
            pushReq
              .response()
              .onComplete(onSuccess(pushResp -> {
              assertEquals(reqStreamDependency, pushResp.request().getStreamPriority().getDependency());
              assertEquals(reqStreamWeight, pushResp.request().getStreamPriority().getWeight());
              checkpoint.succeed();
            }));
          }).setStreamPriority(
            new StreamPriority()
              .setDependency(reqStreamDependency)
              .setWeight(reqStreamWeight)
              .setExclusive(false))
          .send()
          .compose(HttpClientResponse::end))
      .await();
  }

  @Test
  public void testClearTextUpgradeWithBody() throws Exception {
    server = vertx.createHttpServer().requestHandler(req -> {
      req.bodyHandler(body -> req.response().end(body));
    });
    startServer(testAddress);
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
    Buffer body = client
      .request(new RequestOptions(requestOptions).setSsl(false))
      .compose(req -> {
        req.putHeader("Content-Length", "" + payload.length() * 2);
        req.exceptionHandler(err -> Assert.fail(err.getMessage()));
        req.write(payload);
        vertx.setTimer(1000, id -> {
          req.end(payload);
        });
        return req.response()
          .compose(HttpClientResponse::body);
      })
      .await();
    assertEquals(Buffer.buffer().appendBuffer(payload).appendBuffer(payload), body);
  }

  @Test
  public void testClearTextUpgradeWithBodyTooLongFrameResponse(Checkpoint checkpoint) throws Exception {
    Buffer buffer = TestUtils.randomBuffer(1024);
    server = vertx.createHttpServer().requestHandler(req -> {
      req.response().setChunked(true);
      vertx.setPeriodic(1, id -> {
        req.response().write(buffer);
      });
    });
    startServer(testAddress);
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2));
    RequestOptions opts = new RequestOptions(requestOptions).setSsl(false);
    client.request(opts)
      .compose(req ->
        req.setChunked(true)
          .exceptionHandler(err -> {
            if (err instanceof TooLongFrameException) {
              checkpoint.succeed();
            }
          })
          .writeHead()
          .compose(v -> req.response())
      );
  }

  @Test
  public void testSslHandshakeTimeout(Checkpoint checkpoint) throws Exception {
    HttpServerOptions opts = Http2TestBase.createHttp2ServerOptions()
      .setSslHandshakeTimeout(1234)
      .setSslHandshakeTimeoutUnit(TimeUnit.MILLISECONDS);
    server = vertx.createHttpServer(opts)
      .requestHandler(req -> Assert.fail("Should not be called"))
      .exceptionHandler(err -> {
        if (err instanceof SSLHandshakeException) {
          assertEquals("handshake timed out after 1234ms", err.getMessage());
          checkpoint.succeed();
        }
      });
    startServer();
    NetSocket so = vertx.createNetClient().connect(config.port(), config.host()).await();
  }

  @Test
  public void testNonUpgradedH2CConnectionIsEvictedFromThePool(Checkpoint checkpoint) throws Exception {
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2));
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
          client.request(requestOptions)
            .compose(req2 -> req2.send()
              .compose(HttpClientResponse::body))
            .onComplete(checkpoint);
        });
      });
      return req1
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body);
    }).await();
    p.complete();
  }

  /**
   * Test that socket close (without an HTTP/2 go away frame) removes the connection from the pool
   * before the streams are notified. Otherwise a notified stream might reuse a stale connection from
   * the pool.
   */
  @Test
  public void testConnectionCloseEvictsConnectionFromThePoolBeforeStreamsAreClosed(Checkpoint checkpoint) throws Exception {
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
      f2.onComplete(TestUtils.onFailure(v2 -> {
        Future<Buffer> f3 = client.request(new RequestOptions(requestOptions).setURI("/3"))
          .compose(req -> req.send()
            .compose(HttpClientResponse::body));
        f3.onComplete(checkpoint);
      }));
    }));
  }

  @Test
  public void testRstFloodProtection(Checkpoint checkpoint) throws Exception {
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
              checkpoint.succeed();
            });
        }
        req.end().onComplete(onSuccess(v -> {
          req.reset();
        }));
      }));
    }
  }

  @Test
  public void testStreamResetErrorMapping(Checkpoint checkpoint) throws Exception {
    server.requestHandler(req -> {
    });
    startServer(testAddress);
    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
        req.exceptionHandler(err -> {
          assertTrue(err instanceof StreamResetException);
          StreamResetException sre = (StreamResetException) err;
          assertEquals(10, sre.getCode());
          checkpoint.succeed();
        });
        // Force stream allocation
        req.writeHead().onComplete(onSuccess(v -> {
          req.reset(10);
        }));
      }));
  }

  @Test
  public void testUnsupportedAlpnVersion() throws Exception {
    testUnsupportedAlpnVersion(new JdkSSLEngineOptions());
  }

  @Test
  public void testUnsupportedAlpnVersionOpenSSL() throws Exception {
    testUnsupportedAlpnVersion(new OpenSSLEngineOptions());
  }

  private void testUnsupportedAlpnVersion(SSLEngineOptions engine) throws Exception {
    server = vertx.createHttpServer(Http2TestBase.createHttp2ServerOptions()
      .setSslEngineOptions(engine)
      .setAlpnVersions(Collections.singletonList(HttpVersion.HTTP_2))
    );
    server.requestHandler(request -> {
      request.response().end();
    });
    startServer(testAddress);
    client = vertx.createHttpClient(Http2TestBase.createHttp2ClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1));
    try {
      client.request(requestOptions).compose(request -> request.send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end)
        .map(request.version()))
        .await();
      Assert.fail();
    } catch (Exception ignore) {
      // Expected
    }
  }

  @Test
  public void testSendFileCancellation(Checkpoint checkpoint) throws Exception {

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
        .onComplete(TestUtils.onFailure(ar -> {
          assertEquals(0, errors.get());
          checkpoint.succeed();
        }));
    });

    startServer();

    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(resp -> {
          assertEquals(HttpVersion.HTTP_2, resp.version());
          return req.connection().close();
        }))
      .await();
  }

  @Repeat(times = 10)
  @Test
  public void testHttpClientDelayedWriteUponConnectionClose(Checkpoint checkpoint) throws Exception {

    int numVerticles = 5;
    int numWrites = 100;
    int delayCloseMS = 50;

    server.connectionHandler(conn -> {
      vertx.setTimer(delayCloseMS, id -> {
        conn.close();
      });
    });
    server.requestHandler(req -> {
      req.endHandler(v -> {
        req.response().end();
      });
    });

    startServer(testAddress);
    CountDownLatch latch = checkpoint.asLatch(numVerticles);
    vertx.deployVerticle(() -> new VerticleBase() {
      int requestCount;
      int ackCount;
      @Override
      public Future<?> start() throws Exception {
        request();
        return super.start();
      }
      private void request() {
        requestCount++;
        client.request(requestOptions)
          .compose(req -> {
            req.setChunked(true);
            for (int i = 0;i < numWrites;i++) {
              req.write("Hello").onComplete(ar -> {
                ackCount++;
              });
            }
            req.end();
            return req.response().compose(HttpClientResponse::body);
          })
          .onComplete(ar -> {
            if (ar.succeeded()) {
              request();
            } else {
              vertx.setTimer(100, id -> {
                assertEquals(requestCount * numWrites, ackCount);
                latch.countDown();
              });
            }
          });
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER).setInstances(numVerticles));
  }

  @Test
  public void testClientKeepAliveTimeoutNoStreams(Checkpoint checkpoint) throws Exception {
    server = vertx.createHttpServer(Http2TestBase.createHttp2ServerOptions().setInitialSettings(new Http2Settings().setMaxConcurrentStreams(0)));
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer();
    AtomicBoolean closed = new AtomicBoolean();
    client = vertx
      .httpClientBuilder()
      .withConnectHandler(conn -> {
        conn.closeHandler(v -> {
          // We will have retry when the connection is closed
          if (closed.compareAndSet(false, true)) {
            client.close().onComplete(v2 -> {
              checkpoint.succeed();
            });
          }
        });
      })
      .with(Http2TestBase.createHttp2ClientOptions().setHttp2KeepAliveTimeout(1))
      .build();
    client.request(requestOptions);
  }

  @Test
  public void testClearTextDirect() throws Exception {
    server = vertx.createHttpServer(Http2TestBase.createHttp2ServerOptions().setSsl(false).setHttp2ClearTextEnabled(true));
    server.requestHandler(req -> {
      assertFalse(req.isSSL());
      req.response().end();
    });
    startServer();
    client = vertx.createHttpClient(Http2TestBase.createHttp2ClientOptions().setSsl(false).setHttp2ClearTextUpgrade(false));
    client.request(requestOptions).compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .await();
  }
}
