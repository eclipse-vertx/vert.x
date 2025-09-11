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
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.TestUtils;
import io.vertx.test.proxy.HAProxy;
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
  protected NetClientOptions createNetClientOptions() {
    return new NetClientOptions();
  }

  @Override
  protected NetServerOptions createNetServerOptions() {
    return new NetServerOptions();
  }

  @Override
  protected HAProxy createHAProxy(SocketAddress remoteAddress, Buffer header) {
    return new HAProxy(remoteAddress, header);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return Http2TestBase.createHttp2ClientOptions();
  }

  @Override
  protected HttpVersion clientAlpnProtocolVersion() {
    return HttpVersion.HTTP_1_1;
  }

  @Override
  protected HttpVersion serverAlpnProtocolVersion() {
    return HttpVersion.HTTP_2;
  }

  protected void addMoreOptions(HttpServerOptions opts) {
  }

  protected HttpServerOptions setMaxConcurrentStreamsSettings(HttpServerOptions options, int maxConcurrentStreams) {
    return options.setInitialSettings(new Http2Settings().setMaxConcurrentStreams(maxConcurrentStreams));
  }

  protected StreamPriority generateStreamPriority() {
    return new StreamPriority()
      .setDependency(TestUtils.randomPositiveInt())
      .setWeight((short) TestUtils.randomPositiveInt(255))
      .setExclusive(TestUtils.randomBoolean());
  }

  protected StreamPriority defaultStreamPriority() {
    return new StreamPriority()
      .setDependency(0)
      .setWeight(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT)
      .setExclusive(false);
  }

  protected void assertEqualsStreamPriority(StreamPriority expectedStreamPriority,
                                            StreamPriority actualStreamPriority) {
    assertEquals(expectedStreamPriority.getWeight(), actualStreamPriority.getWeight());
    assertEquals(expectedStreamPriority.getDependency(), actualStreamPriority.getDependency());
    assertEquals(expectedStreamPriority.isExclusive(), actualStreamPriority.isExclusive());
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
        .writeHead();
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
          .writeHead();
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
    HttpServerOptions opts = createBaseServerOptions()
      .setPort(DEFAULT_HTTPS_PORT)
      .setHost(DEFAULT_HTTPS_HOST)
      .setUseAlpn(true)
      .setSsl(true)
      .addEnabledCipherSuite("TLS_RSA_WITH_AES_128_CBC_SHA") // Non Diffie-helman -> debuggable in wireshark
      .setKeyCertOptions(Cert.SERVER_PEM.get())
      .setSslEngineOptions(new OpenSSLEngineOptions());
    addMoreOptions(opts);
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
    server = vertx.createHttpServer(setMaxConcurrentStreamsSettings(createBaseServerOptions(), 1));
    server.requestHandler(req -> {
      fail();
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.response().onComplete(onFailure(err -> complete()));
      assertTrue(req.reset().succeeded());
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
    // TODO: generalize this test case for use with both HTTP/2 and HTTP/3
    waitFor(2);
    server.requestHandler(req -> {
      assertEquals(clientAlpnProtocolVersion(), req.version());
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setProtocolVersion(clientAlpnProtocolVersion()).setUseAlpn(false));
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(clientAlpnProtocolVersion(), resp.version());
        complete();
      }));
    await();
  }

  @Test
  public void testServerDoesNotSupportAlpn() throws Exception {
    // TODO: generalize this test case for use with both HTTP/2 and HTTP/3
    waitFor(2);
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setUseAlpn(false));
    server.requestHandler(req -> {
      assertEquals(clientAlpnProtocolVersion(), req.version());
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(clientAlpnProtocolVersion(), resp.version());
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
    // TODO: generalize this test case for use with both HTTP/2 and HTTP/3
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
    // TODO: generalize this test case for use with both HTTP/2 and HTTP/3
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
  public void testStreamPriority() throws Exception {
    waitFor(2);
    StreamPriority requestStreamPriority = generateStreamPriority();
    StreamPriority responseStreamPriority = generateStreamPriority();
    server.requestHandler(req -> {
      assertEqualsStreamPriority(requestStreamPriority, req.streamPriority());
      req.response().setStreamPriority(new StreamPriority(responseStreamPriority));
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .setStreamPriority(new StreamPriority(requestStreamPriority))
        .send().onComplete(onSuccess(resp -> {
          assertEqualsStreamPriority(responseStreamPriority, resp.request().getStreamPriority());
          complete();
        }));
    }));
    await();
  }

  @Test
  public void testStreamPriorityChange() throws Exception {
    StreamPriority requestStreamPriority = generateStreamPriority();
    StreamPriority requestStreamPriority2 = generateStreamPriority();
    StreamPriority responseStreamPriority = generateStreamPriority();
    StreamPriority responseStreamPriority2 = generateStreamPriority();
    waitFor(4);
    server.requestHandler(req -> {
      req.streamPriorityHandler(sp -> {
        assertEqualsStreamPriority(requestStreamPriority2, sp);
        assertEqualsStreamPriority(requestStreamPriority2, req.streamPriority());
        complete();
      });
      assertEqualsStreamPriority(requestStreamPriority, req.streamPriority());
      req.response().setStreamPriority(new StreamPriority(responseStreamPriority));
      req.response().write("hello");
      req.response().setStreamPriority(new StreamPriority(responseStreamPriority2));
      req.response().drainHandler(h -> {
      });
      req.response().end("world");
      complete();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .setStreamPriority(new StreamPriority(requestStreamPriority))
        .response()
        .onComplete(onSuccess(resp -> {
          assertEqualsStreamPriority(responseStreamPriority, resp.request().getStreamPriority());
          resp.streamPriorityHandler(sp -> {
            assertEqualsStreamPriority(responseStreamPriority2, sp);
            assertEqualsStreamPriority(responseStreamPriority2, resp.request().getStreamPriority());
            complete();
          });
          complete();
        }));
      req
        .writeHead()
        .onComplete(h -> {
          req.setStreamPriority(new StreamPriority(requestStreamPriority2));
          req.end();
        });
    }));
    await();
  }

  @Test
  public void testServerStreamPriorityNoChange() throws Exception {
    StreamPriority streamPriority = generateStreamPriority();
    waitFor(2);
    server.requestHandler(req -> {
      req.streamPriorityHandler(sp -> {
        fail("Stream priority handler should not be called " + sp);
      });
      assertEqualsStreamPriority(streamPriority, req.streamPriority());
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
      req.setStreamPriority(new StreamPriority(streamPriority));
      req
        .writeHead()
        .onComplete(h -> {
          req.setStreamPriority(new StreamPriority(streamPriority));
          req.end();
        });
    }));
    await();
  }

  @Test
  public void testClientStreamPriorityNoChange() throws Exception {
    StreamPriority streamPriority = generateStreamPriority();
    waitFor(2);
    server.requestHandler(req -> {
      req.response().setStreamPriority(new StreamPriority(streamPriority));
      req.response().write("hello");
      req.response().setStreamPriority(new StreamPriority(streamPriority));
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
          assertEqualsStreamPriority(streamPriority, resp.request().getStreamPriority());
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
  public void testStreamPriorityInheritance() throws Exception {
    StreamPriority requestStreamPriority = generateStreamPriority();

    waitFor(2);
    server.requestHandler(req -> {
      assertEqualsStreamPriority(requestStreamPriority, req.streamPriority());
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .setStreamPriority(new StreamPriority(requestStreamPriority))
        .send()
        .onComplete(onSuccess(resp -> {
          assertEqualsStreamPriority(requestStreamPriority, resp.request().getStreamPriority());
          complete();
        }));
    }));
    await();
  }

  @Test
  public void testDefaultPriority() throws Exception {
    StreamPriority defaultStreamPriority = defaultStreamPriority();
    waitFor(2);
    server.requestHandler(req -> {
      assertEqualsStreamPriority(defaultStreamPriority, req.streamPriority());
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        assertEqualsStreamPriority(defaultStreamPriority, req.getStreamPriority());
        complete();
      }));
    }));
    await();
  }

  @Test
  public void testStreamPriorityPushPromise() throws Exception {
    StreamPriority pushStreamPriority = generateStreamPriority();
    waitFor(4);
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/pushpath").onComplete(onSuccess(pushedResp -> {
        pushedResp.setStreamPriority(new StreamPriority(pushStreamPriority));
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
            assertEqualsStreamPriority(pushStreamPriority, pushResp.request().getStreamPriority());
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
  public void testStreamPriorityInheritancePushPromise() throws Exception {
    StreamPriority reqStreamPriority = generateStreamPriority();
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
            assertEqualsStreamPriority(reqStreamPriority, pushResp.request().getStreamPriority());
            complete();
          }));
        }).setStreamPriority(new StreamPriority(reqStreamPriority))
        .send()
        .onComplete(onSuccess(resp -> {
          complete();
        }));
    }));
    await();
  }

  @Test
  public void testClearTextUpgradeWithBody() throws Exception {
    // TODO: generalize this test case for use with both HTTP/2 and HTTP/3
    server.close();
    server = vertx.createHttpServer().requestHandler(req -> {
      req.bodyHandler(body -> req.response().end(body));
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setProtocolVersion(serverAlpnProtocolVersion()));
//    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2));
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setProtocolVersion(serverAlpnProtocolVersion()))
//      .with(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2))
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
    // TODO: generalize this test case for use with both HTTP/2 and HTTP/3
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
//    client = vertx.createHttpClient(createBaseClientOptions().setProtocolVersion(serverAlpnProtocolVersion()));
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2));
    client.request(new RequestOptions(requestOptions).setSsl(false)).onComplete(onSuccess(req -> {
      req.response().onComplete(onFailure(err -> {}));
      req.setChunked(true);
      req.exceptionHandler(err -> {
        if (err instanceof TooLongFrameException) {
          testComplete();
        }
      });
      req.writeHead();
    }));
    await();
  }

  @Test
  public void testSslHandshakeTimeout() throws Exception {
    // TODO: generalize this test case for use with both HTTP/2 and HTTP/3
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

  @Ignore("This test does not work on http/2")
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
    // TODO: generalize this test case for use with both HTTP/2 and HTTP/3
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
      req.writeHead().onComplete(onSuccess(v -> {
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
      .setAlpnVersions(Collections.singletonList(serverAlpnProtocolVersion()))
    );
    server.requestHandler(request -> {
      request.response().end();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setProtocolVersion(clientAlpnProtocolVersion()));
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
          assertEquals(serverAlpnProtocolVersion(), resp.version());
          req.connection().close();
        }));
      }));

    await();
  }

  @Repeat(times = 10)
  @Test
  public void testHttpClientDelayedWriteUponConnectionClose() throws Exception {

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
    waitFor(numVerticles);
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
                complete();
              });
            }
          });
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER).setInstances(numVerticles));

    await();
  }

  @Test
  public void testClientKeepAliveTimeoutNoStreams() throws Exception {
    // TODO: generalize this test case for use with both HTTP/2 and HTTP/3
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setInitialSettings(new Http2Settings().setMaxConcurrentStreams(0)));
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer();
    client.close();
    AtomicBoolean closed = new AtomicBoolean();
    client = vertx
      .httpClientBuilder()
      .withConnectHandler(conn -> {
        conn.closeHandler(v -> {
          // We will have retry when the connection is closed
          if (closed.compareAndSet(false, true)) {
            client.close().onComplete(v2 -> {
              testComplete();
            });
          }
        });
      })
      .with(createBaseClientOptions().setHttp2KeepAliveTimeout(1))
      .build();
    client.request(requestOptions).onComplete(ar -> {
      if (ar.succeeded()) {
        ar.result().send();
      }
    });
    await();
  }

  @Test
  public void testClearTextDirect() throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setSsl(false).setHttp2ClearTextEnabled(true));
    server.requestHandler(req -> {
      assertFalse(req.isSSL());
      req.response().end();
    });
    startServer();
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setSsl(false).setHttp2ClearTextUpgrade(false));
    client.request(requestOptions).compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .await();
  }

  @Override
  @Ignore
  @Test
  public void testServerExceptionHandlerOnClose() {
    super.testServerExceptionHandlerOnClose();
  }
}
