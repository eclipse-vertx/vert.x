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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.*;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AsciiString;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.HttpClientConnection;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.TestUtils;
import io.vertx.test.tls.Cert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.zip.GZIPOutputStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ClientTest extends Http2TestBase {

  @Test
  public void testClientSettings() throws Exception {
    waitFor(2);
    io.vertx.core.http.Http2Settings initialSettings = TestUtils.randomHttp2Settings();
    io.vertx.core.http.Http2Settings updatedSettings = TestUtils.randomHttp2Settings();
    updatedSettings.setHeaderTableSize(initialSettings.getHeaderTableSize()); // Otherwise it raise "invalid max dynamic table size" in Netty
    AtomicInteger count = new AtomicInteger();
    Promise<Void> end = Promise.promise();
    server.requestHandler(req -> {
      end.future().onComplete(v -> {
        req.response().end();
      });
    }).connectionHandler(conn -> {
      io.vertx.core.http.Http2Settings initialRemoteSettings = conn.remoteSettings();
      assertEquals(initialSettings.isPushEnabled(), initialRemoteSettings.isPushEnabled());
      assertEquals(initialSettings.getMaxHeaderListSize(), initialRemoteSettings.getMaxHeaderListSize());
      assertEquals(initialSettings.getMaxFrameSize(), initialRemoteSettings.getMaxFrameSize());
      assertEquals(initialSettings.getInitialWindowSize(), initialRemoteSettings.getInitialWindowSize());
//            assertEquals(Math.min(initialSettings.getMaxConcurrentStreams(), Integer.MAX_VALUE), settings.getMaxConcurrentStreams());
      assertEquals(initialSettings.getHeaderTableSize(), initialRemoteSettings.getHeaderTableSize());
      assertEquals(initialSettings.get('\u0007'), initialRemoteSettings.get(7));
      Context ctx = Vertx.currentContext();
      conn.remoteSettingsHandler(settings -> {
        assertOnIOContext(ctx);
        switch (count.getAndIncrement()) {
          case 0:
            // find out why it fails sometimes ...
            // assertEquals(updatedSettings.pushEnabled(), settings.getEnablePush());
            assertEquals(updatedSettings.getMaxHeaderListSize(), settings.getMaxHeaderListSize());
            assertEquals(updatedSettings.getMaxFrameSize(), settings.getMaxFrameSize());
            assertEquals(updatedSettings.getInitialWindowSize(), settings.getInitialWindowSize());
            // find out why it fails sometimes ...
            // assertEquals(Math.min(updatedSettings.maxConcurrentStreams(), Integer.MAX_VALUE), settings.getMaxConcurrentStreams());
            assertEquals(updatedSettings.getHeaderTableSize(), settings.getHeaderTableSize());
            assertEquals(updatedSettings.get('\u0007'), settings.get(7));
            complete();
            break;
          default:
            fail();

        }
      });
    });
    startServer();
    client.close();
    client = vertx.createHttpClient(clientOptions.setInitialSettings(initialSettings));
    client.connectionHandler(conn -> {
      vertx.runOnContext(v -> {
        conn.updateSettings(updatedSettings, ar -> {
          end.complete();
        });
      });
    });
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send(onSuccess(resp -> complete()));
    }));
    await();
  }

  @Test
  public void testInvalidSettings() throws Exception {
    io.vertx.core.http.Http2Settings settings = new io.vertx.core.http.Http2Settings();

    try {
      settings.set(Integer.MAX_VALUE, 0);
      fail("max id should be 0-0xFFFF");
    } catch (RuntimeException e) {
      // expected
    }

    try {
      settings.set(7, -1);
      fail("max value should be 0-0xFFFFFFFF");
    } catch (RuntimeException e) {
      // expected
    }
  }

  @Test
  public void testServerSettings() throws Exception {
    io.vertx.core.http.Http2Settings expectedSettings = TestUtils.randomHttp2Settings();
    expectedSettings.setHeaderTableSize((int)io.vertx.core.http.Http2Settings.DEFAULT_HEADER_TABLE_SIZE);
    Context otherContext = vertx.getOrCreateContext();
    server.connectionHandler(conn -> {
      otherContext.runOnContext(v -> {
        conn.updateSettings(expectedSettings);
      });
    });
    server.requestHandler(req -> {
    });
    startServer();
    AtomicInteger count = new AtomicInteger();
    client.connectionHandler(conn -> {
      conn.remoteSettingsHandler(settings -> {
        switch (count.getAndIncrement()) {
          case 0:
            assertEquals(expectedSettings.getMaxHeaderListSize(), settings.getMaxHeaderListSize());
            assertEquals(expectedSettings.getMaxFrameSize(), settings.getMaxFrameSize());
            assertEquals(expectedSettings.getInitialWindowSize(), settings.getInitialWindowSize());
            assertEquals(expectedSettings.getMaxConcurrentStreams(), settings.getMaxConcurrentStreams());
            assertEquals(expectedSettings.getHeaderTableSize(), settings.getHeaderTableSize());
            assertEquals(expectedSettings.get('\u0007'), settings.get(7));
            testComplete();
            break;
        }
      });
    });
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send(onFailure(resp -> {}));
    }));
    await();
  }

  @Test
  public void testGet() throws Exception {
    ServerBootstrap bootstrap = createH2Server((decoder, encoder) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        vertx.runOnContext(v -> {
          assertTrue(endStream);
          encoder.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("200"), 0, true, ctx.newPromise());
          ctx.flush();
        });
      }
      @Override
      public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {
        vertx.runOnContext(v -> {
          testComplete();
        });
      }
    });
    ChannelFuture s = bootstrap.bind(DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT).sync();
    try {
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.send(onSuccess(resp -> {
          Context ctx = vertx.getOrCreateContext();
          assertOnIOContext(ctx);
          resp.endHandler(v -> {
            assertOnIOContext(ctx);
            resp.request().connection().close();
          });
        }));
      }));
      await();
    } finally {
      s.channel().close().sync();
    }
  }

  @Test
  public void testHeaders() throws Exception {
    AtomicInteger reqCount = new AtomicInteger();
    server.requestHandler(req -> {
      assertEquals("https", req.scheme());
      assertEquals(HttpMethod.GET, req.method());
      assertEquals("/somepath", req.path());
      assertEquals(DEFAULT_HTTPS_HOST_AND_PORT, req.host());
      assertEquals("foo_request_value", req.getHeader("Foo_request"));
      assertEquals("bar_request_value", req.getHeader("bar_request"));
      assertEquals(2, req.headers().getAll("juu_request").size());
      assertEquals("juu_request_value_1", req.headers().getAll("juu_request").get(0));
      assertEquals("juu_request_value_2", req.headers().getAll("juu_request").get(1));
      assertEquals(new HashSet<>(Arrays.asList("foo_request", "bar_request", "juu_request")), new HashSet<>(req.headers().names()));
      reqCount.incrementAndGet();
      HttpServerResponse resp = req.response();
      resp.putHeader("content-type", "text/plain");
      resp.putHeader("Foo_response", "foo_value");
      resp.putHeader("bar_response", "bar_value");
      resp.putHeader("juu_response", (List<String>) Arrays.asList("juu_value_1", "juu_value_2"));
      resp.end();
    });
    startServer();
    client.request(new RequestOptions()
      .setPort(DEFAULT_HTTPS_PORT)
      .setHost(DEFAULT_HTTPS_HOST)
      .setURI("/somepath")
      )
      .onComplete(onSuccess(req -> {
        req.putHeader("Foo_request", "foo_request_value")
          .putHeader("bar_request", "bar_request_value")
          .putHeader("juu_request", Arrays.<String>asList("juu_request_value_1", "juu_request_value_2"));
        req.send(onSuccess(resp -> {
          Context ctx = vertx.getOrCreateContext();
          assertOnIOContext(ctx);
          assertEquals(1, resp.request().streamId());
          assertEquals(1, reqCount.get());
          assertEquals(HttpVersion.HTTP_2, resp.version());
          assertEquals(200, resp.statusCode());
          assertEquals("OK", resp.statusMessage());
          assertEquals("text/plain", resp.getHeader("content-type"));
          assertEquals("foo_value", resp.getHeader("foo_response"));
          assertEquals("bar_value", resp.getHeader("bar_response"));
          assertEquals(2, resp.headers().getAll("juu_response").size());
          assertEquals("juu_value_1", resp.headers().getAll("juu_response").get(0));
          assertEquals("juu_value_2", resp.headers().getAll("juu_response").get(1));
          assertEquals(new HashSet<>(Arrays.asList("content-type", "content-length", "foo_response", "bar_response", "juu_response")), new HashSet<>(resp.headers().names()));
          resp.endHandler(v -> {
            assertOnIOContext(ctx);
            testComplete();
          });
        }));
      }));
    await();
  }

  @Test
  public void testResponseBody() throws Exception {
    testResponseBody(TestUtils.randomAlphaString(100));
  }

  @Test
  public void testEmptyResponseBody() throws Exception {
    testResponseBody("");
  }

  private void testResponseBody(String expected) throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.end(expected);
    });
    startServer();
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send(onSuccess(resp -> {
        AtomicInteger count = new AtomicInteger();
        Buffer content = Buffer.buffer();
        resp.handler(buff -> {
          content.appendBuffer(buff);
          count.incrementAndGet();
        });
        resp.endHandler(v -> {
          assertTrue(count.get() > 0);
          assertEquals(expected, content.toString());
          testComplete();
        });
      }));
    }));
    await();
  }

  @Test
  public void testOverrideAuthority() throws Exception {
    server.requestHandler(req -> {
      assertEquals("localhost:4444", req.host());
      req.response().end();
    });
    startServer(testAddress);
    client.request(new RequestOptions().setServer(testAddress)
      .setPort(4444)
      .setHost("localhost")
    )
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> testComplete()));
    await();
  }

  @Test
  public void testTrailers() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      resp.write("some-content");
      resp.putTrailer("Foo", "foo_value");
      resp.putTrailer("bar", "bar_value");
      resp.putTrailer("juu", (List<String>)Arrays.asList("juu_value_1", "juu_value_2"));
      resp.end();
    });
    startServer();
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send(onSuccess(resp -> {
        assertEquals(null, resp.getTrailer("foo"));
        resp.exceptionHandler(this::fail);
        resp.endHandler(v -> {
          assertEquals("foo_value", resp.getTrailer("foo"));
          assertEquals("foo_value", resp.getTrailer("Foo"));
          assertEquals("bar_value", resp.getTrailer("bar"));
          assertEquals(2, resp.trailers().getAll("juu").size());
          assertEquals("juu_value_1", resp.trailers().getAll("juu").get(0));
          assertEquals("juu_value_2", resp.trailers().getAll("juu").get(1));
          testComplete();
        });
      }));
    }));
    await();
  }

  @Test
  public void testBodyEndHandler() throws Exception {
    // Large body so it will be fragmented in several HTTP2 data frames
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(128 * 1024));
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.end(expected);
    });
    startServer();
    client.request(requestOptions).onSuccess(req -> {
      req.send(onSuccess(resp -> {
        Context ctx = vertx.getOrCreateContext();
        resp.exceptionHandler(this::fail);
        resp.bodyHandler(body -> {
          assertOnIOContext(ctx);
          assertEquals(expected, body);
          testComplete();
        });
      }));
    });
    await();
  }

  @Test
  public void testPost() throws Exception {
    testPost(TestUtils.randomAlphaString(100));
  }

  @Test
  public void testEmptyPost() throws Exception {
    testPost("");
  }

  private void testPost(String expected) throws Exception {
    Buffer content = Buffer.buffer();
    AtomicInteger count = new AtomicInteger();
    server.requestHandler(req -> {
      assertEquals(HttpMethod.POST, req.method());
      req.handler(buff -> {
        content.appendBuffer(buff);
        count.getAndIncrement();
      });
      req.endHandler(v -> {
        assertTrue(count.get() > 0);
        req.response().end();
      });
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions)
      .setMethod(HttpMethod.POST)
    )
      .onComplete(onSuccess(req -> {
      req.response(onSuccess(resp -> {
        resp.endHandler(v -> {
          assertEquals(expected, content.toString());
          testComplete();
        });
      })).end(Buffer.buffer(expected));
    }));
    await();
  }

  @Test
  public void testClientRequestWriteability() throws Exception {
    Buffer content = Buffer.buffer();
    Buffer expected = Buffer.buffer();
    String chunk = TestUtils.randomAlphaString(100);
    CompletableFuture<Void> done = new CompletableFuture<>();
    AtomicBoolean paused = new AtomicBoolean();
    AtomicInteger numPause = new AtomicInteger();
    server.requestHandler(req -> {
      Context ctx = vertx.getOrCreateContext();
      done.thenAccept(v1 -> {
        paused.set(false);
        ctx.runOnContext(v2 -> {
          req.resume();
        });
      });
      numPause.incrementAndGet();
      req.pause();
      paused.set(true);
      req.handler(content::appendBuffer);
      req.endHandler(v -> {
        assertEquals(expected, content);
        req.response().end();
      });
    });
    startServer(testAddress);
    Context ctx = vertx.getOrCreateContext();
    client.close();
    ctx.runOnContext(v -> {
      client = vertx.createHttpClient(createBaseClientOptions());
      client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST)).onComplete(onSuccess(req -> {
        req.response(onSuccess(resp -> {
          testComplete();
        }))
          .setChunked(true)
          .exceptionHandler(err -> {
            fail();
          });
        AtomicInteger sent = new AtomicInteger();
        AtomicInteger count = new AtomicInteger();
        AtomicInteger drained = new AtomicInteger();
        vertx.setPeriodic(1, timerID -> {
          if (req.writeQueueFull()) {
            assertTrue(paused.get());
            assertEquals(1, numPause.get());
            req.drainHandler(v2 -> {
              assertOnIOContext(ctx);
              assertEquals(0, drained.getAndIncrement());
              assertEquals(1, numPause.get());
              assertFalse(paused.get());
              req.end();
            });
            vertx.cancelTimer(timerID);
            done.complete(null);
          } else {
            count.incrementAndGet();
            expected.appendString(chunk);
            req.write(chunk);
            sent.addAndGet(chunk.length());
          }
        });
      }));
    });
    await();
  }

  @Test
  public void testClientResponsePauseResume() throws Exception {
    String content = TestUtils.randomAlphaString(1024);
    Buffer expected = Buffer.buffer();
    Promise<Void> whenFull = Promise.promise();
    AtomicBoolean drain = new AtomicBoolean();
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.putHeader("content-type", "text/plain");
      resp.setChunked(true);
      vertx.setPeriodic(1, timerID -> {
        if (resp.writeQueueFull()) {
          resp.drainHandler(v -> {
            Buffer last = Buffer.buffer("last");
            expected.appendBuffer(last);
            resp.end(last);
            assertEquals(expected.toString().getBytes().length, resp.bytesWritten());
          });
          vertx.cancelTimer(timerID);
          drain.set(true);
          whenFull.complete();
        } else {
          Buffer chunk = Buffer.buffer(content);
          expected.appendBuffer(chunk);
          resp.write(chunk);
        }
      });
    });
    startServer();
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send(onSuccess(resp -> {
        Context ctx = vertx.getOrCreateContext();
        Buffer received = Buffer.buffer();
        resp.pause();
        resp.handler(buff -> {
          if (whenFull.future().isComplete()) {
            assertSame(ctx, Vertx.currentContext());
          } else {
            assertOnIOContext(ctx);
          }
          received.appendBuffer(buff);
        });
        resp.endHandler(v -> {
          assertEquals(expected.toString().length(), received.toString().length());
          testComplete();
        });
        whenFull.future().onComplete(v -> {
          resp.resume();
        });
      }));
    }));
    await();
  }

  @Test
  public void testQueueingRequests() throws Exception {
    testQueueingRequests(100, null);
  }

  @Test
  public void testQueueingRequestsMaxConcurrentStream() throws Exception {
    testQueueingRequests(100, 10L);
  }

  private void testQueueingRequests(int numReq, Long max) throws Exception {
    waitFor(numReq);
    String expected = TestUtils.randomAlphaString(100);
    server.close();
    io.vertx.core.http.Http2Settings serverSettings = new io.vertx.core.http.Http2Settings();
    if (max != null) {
      serverSettings.setMaxConcurrentStreams(max);
    }
    server = vertx.createHttpServer(serverOptions.setInitialSettings(serverSettings));
    server.requestHandler(req -> {
      req.response().end(expected);
    });
    startServer();
    CountDownLatch latch = new CountDownLatch(1);
    client.connectionHandler(conn -> {
      assertEquals(max == null ? 0xFFFFFFFFL : max, conn.remoteSettings().getMaxConcurrentStreams());
      latch.countDown();
    });
    client.request(requestOptions).onComplete(onSuccess(HttpClientRequest::end));
    awaitLatch(latch);
    for (int i = 0;i < numReq;i++) {
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.send(onSuccess(resp -> {
          Buffer content = Buffer.buffer();
          resp.handler(content::appendBuffer);
          resp.endHandler(v -> {
            assertEquals(expected, content.toString());
            complete();
          });
        }));
      }));
    }
    await();
  }

  @Test
  public void testReuseConnection() throws Exception {
    List<SocketAddress> ports = new ArrayList<>();
    server.requestHandler(req -> {
      SocketAddress address = req.remoteAddress();
      assertNotNull(address);
      ports.add(address);
      req.response().end();
    });
    startServer();
    CountDownLatch doReq = new CountDownLatch(1);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send(onSuccess(resp -> {
        resp.endHandler(v -> {
          doReq.countDown();
        });
      }));
    }));
    awaitLatch(doReq);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send(onSuccess(resp -> {
        resp.endHandler(v -> {
          assertEquals(2, ports.size());
          assertEquals(ports.get(0), ports.get(1));
          testComplete();
        });
      }));
    }));
    await();
  }

  @Test
  public void testConnectionFailed() throws Exception {
    client.request(new RequestOptions(requestOptions).setPort(4044)).onComplete(onFailure(err -> {
      Context ctx = Vertx.currentContext();
      assertOnIOContext(ctx);
      assertTrue(err instanceof ConnectException);
      testComplete();
    }));
    await();
  }

  @Test
  public void testFallbackOnHttp1() throws Exception {
    server.close();
    server = vertx.createHttpServer(serverOptions.setUseAlpn(false));
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      req.response().end();
    });
    startServer();
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send(onSuccess(resp -> {
        assertEquals(HttpVersion.HTTP_1_1, resp.version());
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testServerResetClientStreamDuringRequest() throws Exception {
    String chunk = TestUtils.randomAlphaString(1024);
    server.requestHandler(req -> {
      req.handler(buf -> {
        req.response().reset(8);
      });
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST)).onComplete(onSuccess(req -> {
      req.response(onFailure(resp -> {
      }))
        .exceptionHandler(err -> {
          Context ctx = Vertx.currentContext();
          assertOnIOContext(ctx);
          assertTrue(err instanceof StreamResetException);
          StreamResetException reset = (StreamResetException) err;
          assertEquals(8, reset.getCode());
          testComplete();
        })
        .setChunked(true)
        .write(chunk);
    }));
    await();
  }

  @Test
  public void testServerResetClientStreamDuringResponse() throws Exception {
    waitFor(2);
    String chunk = TestUtils.randomAlphaString(1024);
    Promise<Void> doReset = Promise.promise();
    server.requestHandler(req -> {
      doReset.future().onComplete(onSuccess(v -> {
        req.response().reset(8);
      }));
      req.response().setChunked(true).write(Buffer.buffer(chunk));
    });
    startServer(testAddress);
    Context ctx = vertx.getOrCreateContext();
    Handler<Throwable> resetHandler = err -> {
      assertOnIOContext(ctx);
      if (err instanceof StreamResetException) {
        StreamResetException reset = (StreamResetException) err;
        assertEquals(8, reset.getCode());
        complete();
      }
    };
    client.close();
    ctx.runOnContext(v -> {
      client = vertx.createHttpClient(createBaseClientOptions());
      client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST)).onComplete(onSuccess(req -> {
        req.response(onSuccess(resp -> {
          resp.exceptionHandler(resetHandler);
          resp.handler(buff -> {
            doReset.complete();
          });
        }))
          .exceptionHandler(resetHandler)
          .setChunked(true)
          .write(chunk);
      }));
    });
    await();
  }

  @Test
  public void testClientResetServerStreamDuringRequest() throws Exception {
    waitFor(2);
    Promise<Void> bufReceived = Promise.promise();
    server.requestHandler(req -> {
      req.handler(buf -> {
        bufReceived.complete();
      });
      AtomicBoolean errored = new AtomicBoolean();
      req.exceptionHandler(err -> {
        if (errored.compareAndSet(false, true)) {
          assertEquals(err.getClass(), StreamResetException.class);
        }
      });
      AtomicLong reset = new AtomicLong();
      req.response().exceptionHandler(err -> {
        if (err instanceof StreamResetException) {
          reset.set(((StreamResetException) err).getCode());
        }
      });
      req.response().closeHandler(v -> {
        assertEquals(10L, reset.get());
        complete();
      });
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .response(onFailure(err -> {
          complete();
        }))
        .setChunked(true);
      req.write(Buffer.buffer("hello"));
      bufReceived.future().onComplete(ar -> {
        req.reset(10);
      });
    }));
    await();
  }

  @Test
  public void testClientResetServerStreamDuringResponse() throws Exception {
    waitFor(3);
    server.requestHandler(req -> {
      req.exceptionHandler(err -> {
        assertEquals(err.getClass(), StreamResetException.class);
      });
      AtomicLong reset = new AtomicLong();
      req.response().exceptionHandler(err -> {
        if (err instanceof StreamResetException) {
          reset.set(((StreamResetException) err).getCode());
        }
      });
      req.response().closeHandler(v -> {
        assertEquals(10L, reset.get());
        complete();
      });
      req.response().setChunked(true).write(Buffer.buffer("some-data"));
    });
    startServer();
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT)).onComplete(onSuccess(req -> {
      req.send(Buffer.buffer("hello"), onSuccess(resp -> {
        resp.request().reset(10);
        resp.request().write(Buffer.buffer()).onComplete(onFailure(err -> {
          assertEquals(IllegalStateException.class, err.getClass());
          complete();
        }));
        resp.request().end().onComplete(onFailure(err -> {
          assertEquals(IllegalStateException.class, err.getClass());
          complete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testPushPromise() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/wibble?a=b", ar -> {
        assertTrue(ar.succeeded());
        HttpServerResponse response = ar.result();
        response.end("the_content");
      }).end();
    });
    startServer(testAddress);
    AtomicReference<Context> ctx = new AtomicReference<>();
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .pushHandler(pushedReq -> {
          Context current = Vertx.currentContext();
          if (ctx.get() == null) {
            ctx.set(current);
          } else {
            assertSameEventLoop(ctx.get(), current);
          }
          assertOnIOContext(current);
          assertEquals(HttpMethod.GET, pushedReq.getMethod());
          assertEquals("/wibble?a=b", pushedReq.getURI());
          assertEquals("/wibble", pushedReq.path());
          assertEquals("a=b", pushedReq.query());
          pushedReq.response(onSuccess(resp -> {
            assertEquals(200, resp.statusCode());
            Buffer content = Buffer.buffer();
            resp.handler(content::appendBuffer);
            resp.endHandler(v -> {
              complete();
            });
          }));
        })
        .send(onSuccess(resp -> {
          Context current = Vertx.currentContext();
          if (ctx.get() == null) {
            ctx.set(current);
          } else {
            assertSameEventLoop(ctx.get(), current);
          }
          resp.endHandler(v -> {
            complete();
          });
        }));
    }));
    await();
  }

  @Test
  public void testResetActivePushPromise() throws Exception {
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/wibble", ar -> {
        assertTrue(ar.succeeded());
        HttpServerResponse response = ar.result();
        response.exceptionHandler(err -> {
          if (err instanceof StreamResetException) {
            assertEquals(Http2Error.CANCEL.code(), ((StreamResetException) err).getCode());
            testComplete();
          }
        });
        response.setChunked(true).write("some_content");
      });
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.pushHandler(pushedReq -> {
          pushedReq.response(onSuccess(pushedResp -> {
            pushedResp.handler(buff -> {
              pushedReq.reset(Http2Error.CANCEL.code());
            });
          }));
        })
        .send(onFailure(resp -> {
        }));
    }));
    await();
  }

  @Test
  public void testResetPendingPushPromise() throws Exception {
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/wibble", ar -> {
        assertFalse(ar.succeeded());
        testComplete();
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(clientOptions.setInitialSettings(new io.vertx.core.http.Http2Settings().setMaxConcurrentStreams(0L)));
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .pushHandler(pushedReq -> pushedReq.reset(Http2Error.CANCEL.code()))
        .send(onFailure(resp -> {
        }));
    }));
    await();
  }

  @Test
  public void testResetPushPromiseNoHandler() throws Exception {
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/wibble", ar -> {
        assertTrue(ar.succeeded());
        HttpServerResponse resp = ar.result();
        resp.setChunked(true).write("content");
        AtomicLong reset = new AtomicLong();
        resp.exceptionHandler(err -> {
          if (err instanceof StreamResetException) {
            reset.set(((StreamResetException)err).getCode());
          }
        });
        resp.closeHandler(v -> {
          assertEquals(Http2Error.CANCEL.code(), reset.get());
          testComplete();
        });
      });
    });
    startServer();
    client.request(requestOptions).onComplete(onSuccess(HttpClientRequest::end));
    await();
  }

  @Test
  public void testConnectionHandler() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer();
    AtomicReference<HttpConnection> connection = new AtomicReference<>();
    AtomicInteger count = new AtomicInteger();
    client.connectionHandler(conn -> {
      if (count.getAndIncrement() == 0) {
        Context ctx = Vertx.currentContext();
        assertOnIOContext(ctx);
        assertTrue(connection.compareAndSet(null, conn));
      } else {
        fail();
      }
    });
    for (int i = 0;i < 2;i++) {
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.send(onSuccess(resp -> {
          assertSame(connection.get(), resp.request().connection());
          complete();
        }));
      }));
    }
    await();
  }

  @Test
  public void testConnectionShutdownInConnectionHandler() throws Exception {
    waitFor(2);
    AtomicInteger serverStatus = new AtomicInteger();
    server.connectionHandler(conn -> {
      if (serverStatus.getAndIncrement() == 0) {
        conn.goAwayHandler(ga -> {
          assertEquals(0, ga.getErrorCode());
          assertEquals(1, serverStatus.getAndIncrement());
        });
        conn.shutdownHandler(v -> {
          assertEquals(2, serverStatus.getAndIncrement());
        });
        conn.closeHandler(v -> {
          assertEquals(3, serverStatus.getAndIncrement());
        });
      }
    });
    server.requestHandler(req -> {
      assertEquals(5, serverStatus.getAndIncrement());
      req.response().end("" + serverStatus.get());
    });
    startServer(testAddress);
    AtomicInteger clientStatus = new AtomicInteger();
    client.connectionHandler(conn -> {
      Context ctx = Vertx.currentContext();
      if (clientStatus.getAndIncrement() == 0) {
        conn.shutdownHandler(v -> {
          assertOnIOContext(ctx);
          clientStatus.compareAndSet(1, 2);
          complete();
        });
        conn.shutdown();
      }
    });
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .exceptionHandler(err -> complete())
        .send(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          complete();
        }));
    }));
    await();
  }

  @Test
  public void testServerShutdownConnection() throws Exception {
    waitFor(2);
    server.connectionHandler(HttpConnection::shutdown);
    server.requestHandler(req -> fail());
    startServer();
    AtomicInteger count = new AtomicInteger();
    client.connectionHandler(conn -> {
      Context ctx = Vertx.currentContext();
      conn.goAwayHandler(ga -> {
        assertOnIOContext(ctx);
        complete();
      });
    });
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send(onFailure(err -> {
        if (count.getAndIncrement() == 0) {
          complete();
        }
      }));
    }));
    await();
  }

  @Test
  public void testReceivingGoAwayDiscardsTheConnection() throws Exception {
    AtomicInteger reqCount = new AtomicInteger();
    Set<HttpConnection> connections = Collections.synchronizedSet(new HashSet<>());
    server.requestHandler(req -> {
      connections.add(req.connection());
      switch (reqCount.getAndIncrement()) {
        case 0:
          req.connection().goAway(0);
          break;
        case 1:
          req.response().end();
          break;
        default:
          fail();
      }
    });
    startServer(testAddress);
    client.connectionHandler(conn -> {
      AtomicInteger gaCount = new AtomicInteger();
      conn.goAwayHandler(ga -> {
        vertx.runOnContext(v -> {
          client.request(new RequestOptions(requestOptions).setTimeout(5000))
            .compose(HttpClientRequest::send)
            .onComplete(onSuccess(resp2 -> {
              assertEquals(2, connections.size());
              testComplete();
            }));
        });
      });
    });
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send(onFailure(resp -> {}));
    }));
    await();
  }

  @Test
  public void testSendingGoAwayDiscardsTheConnection() throws Exception {
    AtomicInteger reqCount = new AtomicInteger();
    server.requestHandler(req -> {
      switch (reqCount.getAndIncrement()) {
        case 0:
          req.response().setChunked(true).write("some-data");
          break;
        case 1:
          req.response().end();
          break;
        default:
          fail();
      }
    });
    startServer();
    client.request(requestOptions).onComplete(onSuccess(req1 -> {
      req1.send(onSuccess(resp -> {
        resp.request().connection().goAway(0);
        client.request(new RequestOptions()
          .setHost(DEFAULT_HTTPS_HOST)
          .setPort(DEFAULT_HTTPS_PORT)
          .setURI("/somepath")
          .setTimeout(5000)).onComplete(onSuccess(req2 -> {
            req2.send(onSuccess(resp2 -> {
              testComplete();
            }));
        }));
      }));
    }));
    await();
  }

  private Http2ConnectionHandler createHttpConnectionHandler(BiFunction<Http2ConnectionDecoder, Http2ConnectionEncoder, Http2FrameListener> handler) {

    class Handler extends Http2ConnectionHandler {
      public Handler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, io.netty.handler.codec.http2.Http2Settings initialSettings) {
        super(decoder, encoder, initialSettings);
        decoder.frameListener(handler.apply(decoder, encoder));
      }
    }

    class Builder extends AbstractHttp2ConnectionHandlerBuilder<Handler, Builder> {
      @Override
      protected Handler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, io.netty.handler.codec.http2.Http2Settings initialSettings) throws Exception {
        return new Handler(decoder, encoder, initialSettings);
      }
      @Override
      public Handler build() {
        return super.build();
      }
    }

    Builder builder = new Builder();
    return builder.build();
  }

  private ServerBootstrap createH2Server(BiFunction<Http2ConnectionDecoder, Http2ConnectionEncoder, Http2FrameListener> handler) {
    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.channel(NioServerSocketChannel.class);
    NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    eventLoopGroups.add(eventLoopGroup);
    bootstrap.group(eventLoopGroup);
    bootstrap.childHandler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        SSLHelper sslHelper = new SSLHelper(serverOptions, Cert.SERVER_JKS.get(), null);
        SslHandler sslHandler = new SslHandler(sslHelper.setApplicationProtocols(Arrays.asList(HttpVersion.HTTP_2.alpnName(), HttpVersion.HTTP_1_1.alpnName())).createEngine((VertxInternal) vertx, DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT));
        ch.pipeline().addLast(sslHandler);
        ch.pipeline().addLast(new ApplicationProtocolNegotiationHandler("whatever") {
          @Override
          protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
            if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
              ChannelPipeline p = ctx.pipeline();
              Http2ConnectionHandler clientHandler = createHttpConnectionHandler(handler);
              p.addLast("handler", clientHandler);
              return;
            }
            ctx.close();
            throw new IllegalStateException("unknown protocol: " + protocol);
          }
        });
      }
    });
    return bootstrap;
  }

  private ServerBootstrap createH2CServer(BiFunction<Http2ConnectionDecoder, Http2ConnectionEncoder, Http2FrameListener> handler, boolean upgrade) {
    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.channel(NioServerSocketChannel.class);
    bootstrap.group(new NioEventLoopGroup());
    bootstrap.childHandler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        if (upgrade) {
          HttpServerCodec sourceCodec = new HttpServerCodec();
          HttpServerUpgradeHandler.UpgradeCodecFactory upgradeCodecFactory = protocol -> {
            if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
              Http2ConnectionHandler httpConnectionHandler = createHttpConnectionHandler((a, b) -> {
                return new Http2FrameListenerDecorator(handler.apply(a, b)) {
                  @Override
                  public void onSettingsRead(ChannelHandlerContext ctx, io.netty.handler.codec.http2.Http2Settings settings) throws Http2Exception {
                    super.onSettingsRead(ctx, settings);
                    Http2Connection conn = a.connection();
                    Http2Stream stream = conn.stream(1);
                    DefaultHttp2Headers blah = new DefaultHttp2Headers();
                    blah.status("200");
                    b.frameWriter().writeHeaders(ctx, 1, blah, 0, true, ctx.voidPromise());
                  }
                };
              });
              return new Http2ServerUpgradeCodec(httpConnectionHandler);
            } else {
              return null;
            }
          };
          ch.pipeline().addLast(sourceCodec);
          ch.pipeline().addLast(new HttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory));
        } else {
          Http2ConnectionHandler clientHandler = createHttpConnectionHandler(handler);
          ch.pipeline().addLast("handler", clientHandler);
        }
      }
    });
    return bootstrap;
  }

  @Test
  public void testStreamError() throws Exception {
    waitFor(3);
    ServerBootstrap bootstrap = createH2Server((dec, enc) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        enc.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("200"), 0, false, ctx.newPromise());
        // Send a corrupted frame on purpose to check we get the corresponding error in the request exception handler
        // the error is : greater padding value 0c -> 1F
        // ChannelFuture a = encoder.frameWriter().writeData(request.context, id, Buffer.buffer("hello").getByteBuf(), 12, false, request.context.newPromise());
        // normal frame    : 00 00 12 00 08 00 00 00 03 0c 68 65 6c 6c 6f 00 00 00 00 00 00 00 00 00 00 00 00
        // corrupted frame : 00 00 12 00 08 00 00 00 03 1F 68 65 6c 6c 6f 00 00 00 00 00 00 00 00 00 00 00 00
        ctx.channel().write(Buffer.buffer(new byte[]{
            0x00, 0x00, 0x12, 0x00, 0x08, 0x00, 0x00, 0x00, (byte)(streamId & 0xFF), 0x1F, 0x68, 0x65, 0x6c, 0x6c,
            0x6f, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        }).getByteBuf());
        ctx.flush();
      }
    });
    ChannelFuture s = bootstrap.bind(DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT).sync();
    try {
      client.close();
      Context ctx = vertx.getOrCreateContext();
      ctx.runOnContext(v -> {
        client = vertx.createHttpClient(createBaseClientOptions());
        client.connectionHandler(conn -> {
          conn.exceptionHandler(err -> {
            assertOnIOContext(ctx);
            if (err instanceof Http2Exception) {
              complete();
            }
          });
        });
        client.request(new RequestOptions()
          .setMethod(HttpMethod.PUT)
          .setHost(DEFAULT_HTTPS_HOST)
          .setPort(DEFAULT_HTTPS_PORT)
          .setURI(DEFAULT_TEST_URI)
        ).onComplete(onSuccess(req -> {
          req
            .response(onSuccess(resp -> {
              resp.exceptionHandler(err -> {
                assertOnIOContext(ctx);
                if (err instanceof Http2Exception) {
                  complete();
                }
              });
            }))
            .exceptionHandler(err -> {
              assertOnIOContext(ctx);
              if (err instanceof Http2Exception) {
                complete();
              }
            })
            .sendHead();
        }));
      });
      await();
    } finally {
      s.channel().close().sync();
    }
  }

  @Test
  public void testConnectionDecodeError() throws Exception {
    waitFor(3);
    ServerBootstrap bootstrap = createH2Server((dec, enc) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        enc.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("200"), 0, false, ctx.newPromise());
        enc.frameWriter().writeRstStream(ctx, 10, 0, ctx.newPromise());
        ctx.flush();
      }
    });
    ChannelFuture s = bootstrap.bind(DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT).sync();
    try {
      Context ctx = vertx.getOrCreateContext();
      client.close();
      ctx.runOnContext(v -> {
        client = vertx.createHttpClient(createBaseClientOptions());
        client.connectionHandler(conn -> {
          conn.exceptionHandler(err -> {
            assertSame(ctx, Vertx.currentContext());
            if (err instanceof Http2Exception) {
              complete();
            }
          });
        });
        client.request(new RequestOptions()
          .setMethod(HttpMethod.PUT)
          .setHost(DEFAULT_HTTPS_HOST)
          .setPort(DEFAULT_HTTPS_PORT)
          .setURI(DEFAULT_TEST_URI)).onComplete(onSuccess(req -> {
          req.response(onSuccess(resp -> {
            resp.exceptionHandler(err -> {
              assertOnIOContext(ctx);
              if (err instanceof Http2Exception) {
                complete();
              }
            });
          }))
            .exceptionHandler(err -> {
              assertOnIOContext(ctx);
              if (err instanceof Http2Exception) {
                complete();
              }
            })
            .sendHead();
        }));
      });
      await();
    } finally {
      s.channel().close().sync();
    }
  }

  @Test
  public void testInvalidServerResponse() throws Exception {
    ServerBootstrap bootstrap = createH2Server((dec, enc) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        enc.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("xyz"), 0, false, ctx.newPromise());
        ctx.flush();
      }
    });
    ChannelFuture s = bootstrap.bind(DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT).sync();
    try {
      Context ctx = vertx.getOrCreateContext();
      client.close();
      ctx.runOnContext(v -> {
        client = vertx.createHttpClient(createBaseClientOptions());
        client.connectionHandler(conn -> {
          conn.exceptionHandler(err -> fail());
        });
        client.request(requestOptions).onComplete(onSuccess(req -> {
          req.send(onFailure(err -> {
            assertOnIOContext(ctx);
            if (err instanceof NumberFormatException) {
              testComplete();
            }
          }));
        }));
      });
      await();
    } finally {
      s.channel().close().sync();
    }
  }

  @Test
  public void testResponseCompressionEnabled() throws Exception {
    testResponseCompression(true);
  }

  @Test
  public void testResponseCompressionDisabled() throws Exception {
    testResponseCompression(false);
  }

  private void testResponseCompression(boolean enabled) throws Exception {
    byte[] expected = TestUtils.randomAlphaString(1000).getBytes();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GZIPOutputStream in = new GZIPOutputStream(baos);
    in.write(expected);
    in.close();
    byte[] compressed = baos.toByteArray();
    server.requestHandler(req -> {
      assertEquals(enabled ? "deflate, gzip" : null, req.getHeader(HttpHeaderNames.ACCEPT_ENCODING));
      req.response().putHeader(HttpHeaderNames.CONTENT_ENCODING.toLowerCase(), "gzip").end(Buffer.buffer(compressed));
    });
    startServer();
    client.close();
    client = vertx.createHttpClient(clientOptions.setTryUseCompression(enabled));
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send(onSuccess(resp -> {
        String encoding = resp.getHeader(HttpHeaderNames.CONTENT_ENCODING);
        assertEquals(enabled ? null : "gzip", encoding);
        resp.body(onSuccess(buff -> {
          assertEquals(Buffer.buffer(enabled ? expected : compressed), buff);
          testComplete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void test100Continue() throws Exception {
    AtomicInteger status = new AtomicInteger();
    server.close();
    server = vertx.createHttpServer(serverOptions.setHandle100ContinueAutomatically(true));
    server.requestHandler(req -> {
      status.getAndIncrement();
      HttpServerResponse resp = req.response();
      req.bodyHandler(body -> {
        assertEquals(2, status.getAndIncrement());
        assertEquals("request-body", body.toString());
        resp.putHeader("wibble", "wibble-value").end("response-body");
      });
    });
    startServer(testAddress);
    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
        req.putHeader("expect", "100-continue");
        req.response(onSuccess(resp -> {
          assertEquals(3, status.getAndIncrement());
          resp.bodyHandler(body -> {
            assertEquals(4, status.getAndIncrement());
            assertEquals("response-body", body.toString());
            testComplete();
          });
        }));
        req.continueHandler(v -> {
          Context ctx = Vertx.currentContext();
          assertOnIOContext(ctx);
          status.getAndIncrement();
          req.end(Buffer.buffer("request-body"));
        });
        req.sendHead(version -> {
          assertEquals(1, req.streamId());
        });
      }));
    await();
  }

  @Test
  public void testNetSocketConnect() throws Exception {
    waitFor(2);
    final AtomicInteger writeHandlerCounter = new AtomicInteger(0);
    Handler<AsyncResult<Void>> writeHandler = (result) -> {
      if(result.succeeded()) {
        assertTrue(writeHandlerCounter.incrementAndGet() >= 1);
      } else {
        fail(result.cause());
      }
    };

    server.requestHandler(req -> {
      req.toNetSocket(onSuccess(socket -> {
        AtomicInteger status = new AtomicInteger();
        socket.handler(buff -> {
          switch (status.getAndIncrement()) {
            case 0:
              assertEquals(Buffer.buffer("some-data"), buff);
              socket.write(buff, writeHandler);
              break;
            case 1:
              assertEquals(Buffer.buffer("last-data"), buff);
              break;
            default:
              fail();
              break;
          }
        });
        socket.endHandler(v -> {
          assertEquals(2, status.getAndIncrement());
          socket.write(Buffer.buffer("last-data"), writeHandler);
        });
        socket.closeHandler(v -> {
          assertEquals(3, status.getAndIncrement());
          complete();
          assertEquals(2, writeHandlerCounter.get());
        });
      }));
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.CONNECT)).onComplete(onSuccess(req -> {
      req
        .connect(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          NetSocket socket = resp.netSocket();
          StringBuilder received = new StringBuilder();
          AtomicInteger count = new AtomicInteger();
          socket.handler(buff -> {
            if (buff.length() > 0) {
              received.append(buff.toString());
              if (received.toString().equals("some-data")) {
                received.setLength(0);
                socket.end(Buffer.buffer("last-data"));
              } else if (received.toString().equals("last-data")) {
                assertEquals(0, count.getAndIncrement());
              }
            }
          });
          socket.endHandler(v -> {
            assertEquals(1, count.getAndIncrement());
          });
          socket.closeHandler(v -> {
            assertEquals(2, count.getAndIncrement());
            complete();
          });
          socket.write(Buffer.buffer("some-data"));
        }));
    }));
    await();
  }

  @Test
  public void testServerCloseNetSocket() throws Exception {
    waitFor(2);
    AtomicInteger status = new AtomicInteger();
    server.requestHandler(req -> {
      req.toNetSocket(onSuccess(socket -> {
        socket.handler(buff -> {
          switch (status.getAndIncrement()) {
            case 0:
              assertEquals(Buffer.buffer("some-data"), buff);
              socket.end(buff);
              break;
            case 1:
              assertEquals(Buffer.buffer("last-data"), buff);
              break;
            default:
              fail();
              break;
          }
        });
        socket.endHandler(v -> {
          assertEquals(2, status.getAndIncrement());
        });
        socket.closeHandler(v -> {
          assertEquals(3, status.getAndIncrement());
          complete();
        });
      }));
    });

    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.CONNECT)).onComplete(onSuccess(req -> {
      req
        .connect(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          NetSocket socket = resp.netSocket();
          AtomicInteger count = new AtomicInteger();
          socket.handler(buff -> {
            switch (count.getAndIncrement()) {
              case 0:
                assertEquals("some-data", buff.toString());
                break;
              default:
                fail();
                break;
            }
          });
          socket.endHandler(v -> {
            assertEquals(1, count.getAndIncrement());
            socket.end(Buffer.buffer("last-data"));
          });
          socket.closeHandler(v -> {
            assertEquals(2, count.getAndIncrement());
            complete();
          });
          socket.write(Buffer.buffer("some-data"));
        }));
    }));
    await();
  }

  @Test
  public void testSendHeadersCompletionHandler() throws Exception {
    AtomicInteger status = new AtomicInteger();
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer();
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .response(onSuccess(resp -> {
          assertEquals(1, status.getAndIncrement());
          resp.endHandler(v -> {
            assertEquals(2, status.getAndIncrement());
            testComplete();
          });
        }));
      req.sendHead(onSuccess(version -> {
        assertEquals(0, status.getAndIncrement());
        assertSame(HttpVersion.HTTP_2, req.version());
        req.end();
      }));
    }));
    await();
  }

  @Test
  public void testUnknownFrame() throws Exception {
    Buffer expectedSend = TestUtils.randomBuffer(500);
    Buffer expectedRecv = TestUtils.randomBuffer(500);
    server.requestHandler(req -> {
      req.customFrameHandler(frame -> {
        assertEquals(10, frame.type());
        assertEquals(253, frame.flags());
        assertEquals(expectedSend, frame.payload());
        req.response().writeCustomFrame(12, 134, expectedRecv).end();
      });
    });
    startServer(testAddress);
    AtomicInteger status = new AtomicInteger();
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.response(onSuccess(resp -> {
        Context ctx = Vertx.currentContext();
        assertEquals(0, status.getAndIncrement());
        resp.customFrameHandler(frame -> {
          assertOnIOContext(ctx);
          assertEquals(1, status.getAndIncrement());
          assertEquals(12, frame.type());
          assertEquals(134, frame.flags());
          assertEquals(expectedRecv, frame.payload());
        });
        resp.endHandler(v -> {
          assertEquals(2, status.getAndIncrement());
          testComplete();
        });
      }));
      req.sendHead(onSuccess(version -> {
        assertSame(HttpVersion.HTTP_2, req.version());
        req.writeCustomFrame(10, 253, expectedSend);
        req.end();
      }));
    }));
    await();
  }

  @Test
  public void testClearTextUpgrade() throws Exception {
    testClearText(true);
  }

  @Test
  public void testClearTextWithPriorKnowledge() throws Exception {
    testClearText(false);
  }

  private void testClearText(boolean upgrade) throws Exception {
    Assume.assumeTrue(testAddress.isInetSocket());
    ServerBootstrap bootstrap = createH2CServer((dec, enc) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        enc.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("200"), 0, true, ctx.newPromise());
        ctx.flush();
      }
    }, upgrade);
    ChannelFuture s = bootstrap.bind(DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT).sync();
    try {
      client.close();
      client = vertx.createHttpClient(clientOptions.setUseAlpn(false).setSsl(false).setHttp2ClearTextUpgrade(upgrade));
      client.request(requestOptions).onComplete(onSuccess(req1 -> {
        req1.send(onSuccess(resp1 -> {
          HttpConnection conn = resp1.request().connection();
          assertEquals(HttpVersion.HTTP_2, resp1.version());
          client.request(requestOptions).onComplete(onSuccess(req2 -> {
            req2.send(onSuccess(resp2 -> {
              assertSame(((HttpClientConnection)conn).channel(), ((HttpClientConnection)resp2.request().connection()).channel());
              testComplete();
            }));
          }));
        }));
      }));
      await();
    } finally {
      s.channel().close().sync();
    }
  }

  @Test
  public void testRejectClearTextUpgrade() throws Exception {
    System.setProperty("vertx.disableH2c", "true");
    try {
      server.close();
      server = vertx.createHttpServer(serverOptions.setUseAlpn(false).setSsl(false));
      server.requestHandler(req -> {
        MultiMap headers = req.headers();
        String upgrade = headers.get("upgrade");
        assertEquals(DEFAULT_HTTPS_HOST + ":" + DEFAULT_HTTPS_PORT, req.host());
        if ("h2c".equals(upgrade)) {
          req.response().setStatusCode(400).end();
        } else {
          req.response().end("wibble");
        }
        assertEquals(HttpVersion.HTTP_1_1, req.version());
      });
      startServer(testAddress);
      client.close();
      client = vertx.createHttpClient(clientOptions.setUseAlpn(false).setSsl(false));
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.send(onSuccess(resp -> {
          assertEquals(400, resp.statusCode());
          assertEquals(HttpVersion.HTTP_1_1, resp.version());
          resp.bodyHandler(body -> {
            testComplete();
          });
        }));
      }));
      await();
    } finally {
      System.clearProperty("vertx.disableH2c");
    }
  }

  @Test
  public void testIdleTimeout() throws Exception {
    testIdleTimeout(serverOptions, clientOptions.setDefaultPort(DEFAULT_HTTPS_PORT));
  }

  @Test
  public void testIdleTimeoutClearTextUpgrade() throws Exception {
    testIdleTimeout(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTPS_HOST),
        clientOptions.setDefaultPort(DEFAULT_HTTP_PORT).setUseAlpn(false).setSsl(false).setHttp2ClearTextUpgrade(true));
  }

  @Test
  public void testIdleTimeoutClearTextDirect() throws Exception {
    testIdleTimeout(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTPS_HOST),
        clientOptions.setDefaultPort(DEFAULT_HTTP_PORT).setUseAlpn(false).setSsl(false).setHttp2ClearTextUpgrade(false));
  }

  private void testIdleTimeout(HttpServerOptions serverOptions, HttpClientOptions clientOptions) throws Exception {
    waitFor(3);
    server.close();
    server = vertx.createHttpServer(serverOptions);
    server.requestHandler(req -> {
      req.connection().closeHandler(v -> {
        complete();
      });
      req.response().setChunked(true).write("somedata");
    });
    startServer(testAddress);
    client.close();
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      client = vertx.createHttpClient(clientOptions.setIdleTimeout(2));
      client.connectionHandler(conn -> {
        conn.closeHandler(v2 -> {
          assertSame(ctx, Vertx.currentContext());
          complete();
        });
      });
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req
          .exceptionHandler(err -> {
            complete();
          });
        req.sendHead();
      }));
    });
    await();
  }

  @Test
  public void testIdleTimoutNoConnections() throws Exception {
    waitFor(4);
    AtomicLong time = new AtomicLong();
    server.requestHandler(req -> {
      req.connection().closeHandler(v -> {
        complete();
      });
      req.response().end("somedata");
      complete();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(clientOptions.setHttp2KeepAliveTimeout(5).setIdleTimeout(2));
    client.connectionHandler(conn -> {
      conn.closeHandler(v -> {
        assertTrue(System.currentTimeMillis() - time.get() > 1000);
        complete();
      });
    });
    client.request(requestOptions)
      .compose(req -> req.send().compose(HttpClientResponse::body))
      .onComplete(onSuccess(resp -> {
        time.set(System.currentTimeMillis());
        complete();
      }));
    await();
  }

  @Test
  public void testDisableIdleTimeoutClearTextUpgrade() throws Exception {
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions()
      .setPort(8080)
      .setHost("localhost"));
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer();
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions()
      .setIdleTimeout(2)
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setDefaultPort(8080)
      .setDefaultHost("localhost"));
    client.request(HttpMethod.GET, "/somepath")
      .compose(req -> req.send().compose(HttpClientResponse::body))
      .onComplete(onSuccess(body1 -> {
        vertx.setTimer(10, id1 -> {
          client.request(HttpMethod.GET, "/somepath")
            .compose(req -> req.send().compose(HttpClientResponse::body))
            .onComplete(onSuccess(body2 -> {
              testComplete();
            }));
        });
      }));
    await();
  }

  @Test
  public void testSendPing() throws Exception {
    waitFor(2);
    Buffer expected = TestUtils.randomBuffer(8);
    Context ctx = vertx.getOrCreateContext();
    server.connectionHandler(conn -> {
      conn.pingHandler(data -> {
        assertEquals(expected, data);
        complete();
      });
    });
    server.requestHandler(req -> {});
    startServer(ctx);
    client.connectionHandler(conn -> {
      conn.ping(expected, ar -> {
        assertTrue(ar.succeeded());
        Buffer buff = ar.result();
        assertEquals(expected, buff);
        complete();
      });
    });
    client.request(requestOptions).onComplete(onSuccess(HttpClientRequest::send));
    await();
  }

  @Test
  public void testReceivePing() throws Exception {
    Buffer expected = TestUtils.randomBuffer(8);
    Context ctx = vertx.getOrCreateContext();
    server.connectionHandler(conn -> {
      conn.ping(expected, ar -> {

      });
    });
    server.requestHandler(req -> {});
    startServer(ctx);
    client.connectionHandler(conn -> {
      conn.pingHandler(data -> {
        assertEquals(expected, data);
        complete();
      });
    });
    client.request(requestOptions).onComplete(onSuccess(HttpClientRequest::send));
    await();
  }

  @Test
  public void testMaxConcurrencySingleConnection() throws Exception {
    testMaxConcurrency(1, 5);
  }

  @Test
  public void testMaxConcurrencyMultipleConnections() throws Exception {
    testMaxConcurrency(2, 1);
  }

  private void testMaxConcurrency(int poolSize, int maxConcurrency) throws Exception {
    int rounds = 1 + poolSize;
    int maxRequests = poolSize * maxConcurrency;
    int totalRequests = maxRequests + maxConcurrency;
    Set<HttpConnection> serverConns = new HashSet<>();
    server.connectionHandler(conn -> {
      serverConns.add(conn);
      assertTrue(serverConns.size() <= poolSize);
    });
    ArrayList<HttpServerRequest> requests = new ArrayList<>();
    server.requestHandler(req -> {
      if (requests.size() < maxRequests) {
        requests.add(req);
        if (requests.size() == maxRequests) {
          vertx.setTimer(300, v -> {
            assertEquals(maxRequests, requests.size());
            requests.forEach(r -> r.response().end());
          });
        }
      } else {
        req.response().end();
      }
    });
    startServer();
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions(clientOptions).
        setHttp2MaxPoolSize(poolSize).
        setHttp2MultiplexingLimit(maxConcurrency));
    AtomicInteger respCount = new AtomicInteger();
    Set<HttpConnection> clientConnections = Collections.synchronizedSet(new HashSet<>());
    client.connectionHandler(clientConnections::add);
    for (int j = 0;j < rounds;j++) {
      for (int i = 0;i < maxConcurrency;i++) {
        client.request(requestOptions).onComplete(onSuccess(req -> {
          req.send(onSuccess(resp -> {
            resp.endHandler(v -> {
              if (respCount.incrementAndGet() == totalRequests) {
                testComplete();
              }
            });
          }));
        }));
      }
      if (j < poolSize) {
        int threshold = j + 1;
        AsyncTestBase.assertWaitUntil(() -> clientConnections.size() == threshold);
      }
    }

    await();
  }

  @Test
  public void testConnectionWindowSize() throws Exception {
    ServerBootstrap bootstrap = createH2Server((decoder, encoder) -> new Http2EventAdapter() {
      @Override
      public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) throws Http2Exception {
        vertx.runOnContext(v -> {
          assertEquals(65535, windowSizeIncrement);
          testComplete();
        });
      }
    });
    ChannelFuture s = bootstrap.bind(DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT).sync();
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions(clientOptions).setHttp2ConnectionWindowSize(65535 * 2));
    client.request(requestOptions).onComplete(onSuccess(HttpClientRequest::send));
    await();
  }

  @Test
  public void testUpdateConnectionWindowSize() throws Exception {
    ServerBootstrap bootstrap = createH2Server((decoder, encoder) -> new Http2EventAdapter() {
      @Override
      public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) throws Http2Exception {
        vertx.runOnContext(v -> {
          assertEquals(65535, windowSizeIncrement);
          testComplete();
        });
      }
    });
    ChannelFuture s = bootstrap.bind(DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT).sync();
    client.connectionHandler(conn -> {
      assertEquals(65535, conn.getWindowSize());
      conn.setWindowSize(65535 + 10000);
      assertEquals(65535 + 10000, conn.getWindowSize());
      conn.setWindowSize(65535 + 65535);
      assertEquals(65535 + 65535, conn.getWindowSize());
    });
    client.request(requestOptions).onComplete(onSuccess(HttpClientRequest::send));
    await();
  }

/*
  @Test
  public void testFillsSingleConnection() throws Exception {

    Set<HttpConnection> serverConns = new HashSet<>();
    List<HttpServerRequest> requests = new ArrayList<>();
    server.requestHandler(req -> {
      requests.add(req);
      serverConns.add(req.connection());
      if (requests.size() == 10) {
        System.out.println("requestsPerConn = " + serverConns);
      }
    });
    startServer();

    client.close();
    client = vertx.createHttpClient(new HttpClientOptions(clientOptions).
        setHttp2MaxPoolSize(2).
        setHttp2MaxStreams(10));
    AtomicInteger respCount = new AtomicInteger();
    for (int i = 0;i < 10;i++) {
      client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
        resp.endHandler(v -> {
        });
      });
    }
    await();
  }
*/

  @Test
  public void testStreamPriority() throws Exception {
    StreamPriority requestStreamPriority = new StreamPriority().setDependency(123).setWeight((short)45).setExclusive(true);
    StreamPriority responseStreamPriority = new StreamPriority().setDependency(153).setWeight((short)75).setExclusive(false);
    waitFor(2);
    ServerBootstrap bootstrap = createH2Server((decoder, encoder) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        vertx.runOnContext(v -> {
          assertEquals(requestStreamPriority.getDependency(), streamDependency);
          assertEquals(requestStreamPriority.getWeight(), weight);
          assertEquals(requestStreamPriority.isExclusive(), exclusive);
          encoder.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("200"), responseStreamPriority.getDependency(), responseStreamPriority.getWeight(), responseStreamPriority.isExclusive(), 0, true, ctx.newPromise());
          ctx.flush();
          if(endStream)
            complete();
        });
      }
    });
    ChannelFuture s = bootstrap.bind(DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT).sync();
    try {
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req
          .setStreamPriority(requestStreamPriority)
          .send(onSuccess(resp -> {
            assertEquals(responseStreamPriority, resp.request().getStreamPriority());
            Context ctx = vertx.getOrCreateContext();
            assertOnIOContext(ctx);
            resp.endHandler(v -> {
              complete();
            });
          }));
      }));
      await();
    } finally {
      s.channel().close().sync();
    }
  }

  @Test
  public void testStreamPriorityChange() throws Exception {
    StreamPriority requestStreamPriority = new StreamPriority().setDependency(123).setWeight((short)45).setExclusive(true);
    StreamPriority requestStreamPriority2 = new StreamPriority().setDependency(223).setWeight((short)145).setExclusive(false);
    StreamPriority responseStreamPriority = new StreamPriority().setDependency(153).setWeight((short)75).setExclusive(false);
    StreamPriority responseStreamPriority2 = new StreamPriority().setDependency(253).setWeight((short)175).setExclusive(true);
    waitFor(5);
    ServerBootstrap bootstrap = createH2Server((decoder, encoder) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        vertx.runOnContext(v -> {
          assertEquals(requestStreamPriority.getDependency(), streamDependency);
          assertEquals(requestStreamPriority.getWeight(), weight);
          assertEquals(requestStreamPriority.isExclusive(), exclusive);
          assertFalse(endStream);
          complete();
        });
      }
      @Override
      public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) throws Http2Exception {
        vertx.runOnContext(v -> {
          assertEquals(requestStreamPriority2.getDependency(), streamDependency);
          assertEquals(requestStreamPriority2.getWeight(), weight);
          assertEquals(requestStreamPriority2.isExclusive(), exclusive);
          complete();
        });
      }
      @Override
      public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          if(endOfStream) {
            encoder.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("200"), responseStreamPriority.getDependency(), responseStreamPriority.getWeight(), responseStreamPriority.isExclusive(), 0, false, ctx.newPromise());
            ctx.flush();
            encoder.writePriority(ctx, streamId, responseStreamPriority2.getDependency(), responseStreamPriority2.getWeight(), responseStreamPriority2.isExclusive(), ctx.newPromise());
            ctx.flush();
            encoder.writeData(ctx, streamId, Buffer.buffer("hello").getByteBuf(), 0, true, ctx.newPromise());
            ctx.flush();
            vertx.runOnContext(v -> {
              complete();
            });
          }
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
      }

    });
    ChannelFuture s = bootstrap.bind(DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT).sync();
    try {
      client.request(new RequestOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)
        .setURI("/somepath")).onComplete(onSuccess(req -> {
        req
          .response(onSuccess(resp -> {
            assertEquals(responseStreamPriority, resp.request().getStreamPriority());
            Context ctx = vertx.getOrCreateContext();
            assertOnIOContext(ctx);
            resp.streamPriorityHandler(streamPriority -> {
              assertOnIOContext(ctx);
              assertEquals(responseStreamPriority2, streamPriority);
              assertEquals(responseStreamPriority2, resp.request().getStreamPriority());
              complete();
            });
            resp.endHandler(v -> {
              assertOnIOContext(ctx);
              assertEquals(responseStreamPriority2, resp.request().getStreamPriority());
              complete();
            });
          }))
          .setStreamPriority(requestStreamPriority);
        req.sendHead(h -> {
          req.setStreamPriority(requestStreamPriority2);
          req.end();
        });
      }));
      await();
    } finally {
      s.channel().close().sync();
    }
  }

  @Ignore("Cannot pass reliably for now (https://github.com/netty/netty/issues/9842)")
  @Test
  public void testClientStreamPriorityNoChange() throws Exception {
    StreamPriority streamPriority = new StreamPriority().setDependency(123).setWeight((short)45).setExclusive(true);
    waitFor(2);
    Promise<Void> latch = Promise.promise();
    ServerBootstrap bootstrap = createH2Server((decoder, encoder) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        vertx.runOnContext(v -> {
          assertEquals(streamPriority.getDependency(), streamDependency);
          assertEquals(streamPriority.getWeight(), weight);
          assertEquals(streamPriority.isExclusive(), exclusive);
          assertFalse(endStream);
          latch.complete();
        });
        encoder.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("200"),0, true, ctx.newPromise());
        ctx.flush();
      }
      @Override
      public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) throws Http2Exception {
        fail("Priority frame should not be sent");
      }
      @Override
      public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          if(endOfStream) {
            vertx.runOnContext(v -> {
              complete();
            });
          }
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
      }
    });
    ChannelFuture s = bootstrap.bind(DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT).sync();
    try {
      client.request(new RequestOptions()
        .setHost(DEFAULT_HTTPS_HOST)
        .setPort(DEFAULT_HTTPS_PORT)
        .setURI("/somepath")).onComplete(onSuccess(req -> {
        req
          .response(onSuccess(resp -> {
            resp.endHandler(v -> {
              complete();
            });
          }))
          .setStreamPriority(streamPriority);
        req.sendHead();
        latch.future().onComplete(onSuccess(v -> {
          req.setStreamPriority(streamPriority);
          req.end();
        }));
      }));
      await();
    } finally {
      s.channel().close().sync();
    }
  }

  @Ignore("Cannot pass reliably for now (https://github.com/netty/netty/issues/9842)")
  @Test
  public void testServerStreamPriorityNoChange() throws Exception {
    StreamPriority streamPriority = new StreamPriority().setDependency(123).setWeight((short)45).setExclusive(true);
    waitFor(1);
    ServerBootstrap bootstrap = createH2Server((decoder, encoder) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        encoder.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("200"), streamPriority.getDependency(), streamPriority.getWeight(), streamPriority.isExclusive(), 0, false, ctx.newPromise());
        encoder.writePriority(ctx, streamId, streamPriority.getDependency(), streamPriority.getWeight(), streamPriority.isExclusive(), ctx.newPromise());
        encoder.writeData(ctx, streamId, Buffer.buffer("hello").getByteBuf(), 0, true, ctx.newPromise());
        ctx.flush();
      }
      @Override
      public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) throws Http2Exception {
        fail("Priority frame should not be sent");
      }
    });
    ChannelFuture s = bootstrap.bind(DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT).sync();
    try {
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.send(onSuccess(resp -> {
          assertEquals(streamPriority, resp.request().getStreamPriority());
          Context ctx = vertx.getOrCreateContext();
          assertOnIOContext(ctx);
          resp.streamPriorityHandler(priority -> fail("Stream priority handler should not be called"));
          resp.endHandler(v -> {
            assertEquals(streamPriority, resp.request().getStreamPriority());
            complete();
          });
        }));
      }));
      await();
    } finally {
      s.channel().close().sync();
    }
  }
}
