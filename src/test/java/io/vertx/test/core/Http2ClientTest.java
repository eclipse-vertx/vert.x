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

import io.netty.bootstrap.ServerBootstrap;
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
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AsciiString;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.KeyStoreHelper;
import io.vertx.core.net.impl.SSLHelper;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.zip.GZIPOutputStream;

import static io.vertx.test.core.TestUtils.assertIllegalStateException;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ClientTest extends Http2TestBase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    clientOptions = new HttpClientOptions().
        setUseAlpn(true).
        setTrustStoreOptions((JksOptions) TLSCert.JKS.getClientTrustOptions()).
        setProtocolVersion(HttpVersion.HTTP_2);
    client = vertx.createHttpClient(clientOptions);
  }

  @Test
  public void testClientSettings() throws Exception {
    waitFor(2);
    io.vertx.core.http.Http2Settings initialSettings = TestUtils.randomHttp2Settings();
    io.vertx.core.http.Http2Settings updatedSettings = TestUtils.randomHttp2Settings();
    updatedSettings.setHeaderTableSize(initialSettings.getHeaderTableSize()); // Otherwise it raise "invalid max dynamic table size" in Netty
    AtomicInteger count = new AtomicInteger();
    Future<Void> end = Future.future();
    server.requestHandler(req -> {
      end.setHandler(v -> {
        req.response().end();
      });
    }).connectionHandler(conn -> {
      Context ctx = Vertx.currentContext();
      conn.remoteSettingsHandler(settings -> {
        assertOnIOContext(ctx);
        switch (count.getAndIncrement()) {
          case 0:
            assertEquals(initialSettings.isPushEnabled(), settings.isPushEnabled());
            assertEquals(initialSettings.getMaxHeaderListSize(), settings.getMaxHeaderListSize());
            assertEquals(initialSettings.getMaxFrameSize(), settings.getMaxFrameSize());
            assertEquals(initialSettings.getInitialWindowSize(), settings.getInitialWindowSize());
//            assertEquals(Math.min(initialSettings.getMaxConcurrentStreams(), Integer.MAX_VALUE), settings.getMaxConcurrentStreams());
            assertEquals(initialSettings.getHeaderTableSize(), settings.getHeaderTableSize());
            assertEquals(initialSettings.get('\u0007'), settings.get(7));
            break;
          case 1:
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
        }
      });
    });
    startServer();
    client.close();
    client = vertx.createHttpClient(clientOptions.setInitialSettings(initialSettings));
    client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      complete();
    }).connectionHandler(conn -> {
      conn.updateSettings(updatedSettings, ar -> {
        end.complete();
      });
    }).end();
    await();
  }

  @Test
  public void testServerSettings() throws Exception {
    waitFor(2);
    io.vertx.core.http.Http2Settings expectedSettings = TestUtils.randomHttp2Settings();
    expectedSettings.setHeaderTableSize((int)io.vertx.core.http.Http2Settings.DEFAULT_HEADER_TABLE_SIZE);
    server.close();
    server = vertx.createHttpServer(serverOptions);
    Context otherContext = vertx.getOrCreateContext();
    server.connectionHandler(conn -> {
      otherContext.runOnContext(v -> {
        conn.updateSettings(expectedSettings);
      });
    });
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer();
    AtomicInteger count = new AtomicInteger();
    client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      complete();
    }).connectionHandler(conn -> {
      conn.remoteSettingsHandler(settings -> {
        switch (count.getAndIncrement()) {
          case 0:
            // Initial settings
            break;
          case 1:
            assertEquals(expectedSettings.getMaxHeaderListSize(), settings.getMaxHeaderListSize());
            assertEquals(expectedSettings.getMaxFrameSize(), settings.getMaxFrameSize());
            assertEquals(expectedSettings.getInitialWindowSize(), settings.getInitialWindowSize());
            assertEquals(expectedSettings.getMaxConcurrentStreams(), settings.getMaxConcurrentStreams());
            assertEquals(expectedSettings.getHeaderTableSize(), settings.getHeaderTableSize());
            assertEquals(expectedSettings.get('\u0007'), settings.get(7));
            complete();
            break;
        }
      });
    }).end();
    await();
  }

  @Test
  public void testGet() throws Exception {
    String expected = TestUtils.randomAlphaString(100);
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
      reqCount.incrementAndGet();
      HttpServerResponse resp = req.response();
      resp.putHeader("content-type", "text/plain");
      resp.putHeader("Foo_response", "foo_value");
      resp.putHeader("bar_response", "bar_value");
      resp.putHeader("juu_response", (List<String>) Arrays.asList("juu_value_1", "juu_value_2"));
      resp.end(expected);
    });
    startServer();
    HttpClientRequest req = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath");
    req.handler(resp -> {
      Context ctx = vertx.getOrCreateContext();
      assertOnIOContext(ctx);
      assertEquals(3, req.streamId());
      assertEquals(1, reqCount.get());
      assertEquals(HttpVersion.HTTP_2, resp.version());
      assertEquals(200, resp.statusCode());
      assertEquals("OK", resp.statusMessage());
      assertEquals("text/plain", resp.getHeader("content-type"));
      assertEquals("200", resp.getHeader(":status"));
      assertEquals("foo_value", resp.getHeader("foo_response"));
      assertEquals("bar_value", resp.getHeader("bar_response"));
      assertEquals(2, resp.headers().getAll("juu_response").size());
      assertEquals("juu_value_1", resp.headers().getAll("juu_response").get(0));
      assertEquals("juu_value_2", resp.headers().getAll("juu_response").get(1));
      Buffer content = Buffer.buffer();
      resp.handler(buff -> {
        assertOnIOContext(ctx);
        content.appendBuffer(buff);
      });
      resp.endHandler(v -> {
        assertOnIOContext(ctx);
        assertEquals(expected, content.toString());
        testComplete();
      });
    })
        .putHeader("Foo_request", "foo_request_value")
        .putHeader("bar_request", "bar_request_value")
        .putHeader("juu_request", Arrays.<CharSequence>asList("juu_request_value_1", "juu_request_value_2"))
        .exceptionHandler(err -> fail())
        .end();
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
    client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
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
    })
        .exceptionHandler(err -> fail())
        .end();
    await();
  }

  @Test
  public void testOverrideAuthority() throws Exception {
    server.requestHandler(req -> {
      assertEquals("localhost:4444", req.host());
      req.response().end();
    });
    startServer();
    client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      testComplete();
    })
        .setHost("localhost:4444")
        .exceptionHandler(err -> fail())
        .end();
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
    client.getNow(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepeth", resp -> {
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
    });
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
    client.getNow(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      Context ctx = vertx.getOrCreateContext();
      resp.exceptionHandler(this::fail);
      resp.bodyHandler(body -> {
        assertOnIOContext(ctx);
        assertEquals(expected, body);
        testComplete();
      });
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
    startServer();
    client.post(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      resp.endHandler(v -> {
        assertEquals(expected, content.toString());
        testComplete();
      });
    }).exceptionHandler(err -> {
      fail();
    }).end(expected);
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
    startServer();
    HttpClientRequest req = client.post(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      testComplete();
    }).setChunked(true).exceptionHandler(err -> {
      fail();
    });
    AtomicInteger sent = new AtomicInteger();
    AtomicInteger count = new AtomicInteger();
    AtomicInteger drained = new AtomicInteger();
    vertx.setPeriodic(1, timerID -> {
      Context ctx = vertx.getOrCreateContext();
      if (req.writeQueueFull()) {
        assertTrue(paused.get());
        assertEquals(1, numPause.get());
        req.drainHandler(v -> {
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
    await();
  }

  @Test
  public void testClientResponsePauseResume() throws Exception {
    String content = TestUtils.randomAlphaString(1024);
    Buffer expected = Buffer.buffer();
    Future<Void> whenFull = Future.future();
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
    client.getNow(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      Context ctx = vertx.getOrCreateContext();
      Buffer received = Buffer.buffer();
      resp.pause();
      resp.handler(buff -> {
        if (whenFull.isComplete()) {
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
      whenFull.setHandler(v -> {
        resp.resume();
      });
    });
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
    if (max != null) {
      server.close();
      server = vertx.createHttpServer(serverOptions.setInitialSettings(new io.vertx.core.http.Http2Settings().setMaxConcurrentStreams(10L)));
    }
    server.requestHandler(req -> {
      req.response().end(expected);
    });
    startServer();
    CountDownLatch latch = new CountDownLatch(1);
    client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
    }).connectionHandler(conn -> {
      conn.remoteSettingsHandler(settings -> {
        assertEquals(max == null ? 0xFFFFFFFFL : max, settings.getMaxConcurrentStreams());
        latch.countDown();
      });
    }).exceptionHandler(err -> {
      fail();
    }).end();
    awaitLatch(latch);
    for (int i = 0;i < numReq;i++) {
      client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
        Buffer content = Buffer.buffer();
        resp.handler(content::appendBuffer);
        resp.endHandler(v -> {
          assertEquals(expected, content.toString());
          complete();
        });
      }).exceptionHandler(err -> {
        fail();
      }).end();
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
    client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      resp.endHandler(v -> {
        doReq.countDown();
      });
    }).exceptionHandler(err -> {
      fail();
    }).end();
    awaitLatch(doReq);
    client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      resp.endHandler(v -> {
        assertEquals(2, ports.size());
        assertEquals(ports.get(0), ports.get(1));
        testComplete();
      });
    }).exceptionHandler(err -> {
      fail();
    }).end();
    await();
  }

  @Test
  public void testConnectionFailed() throws Exception {
    client.get(4044, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
    }).exceptionHandler(err -> {
      Context ctx = Vertx.currentContext();
      assertOnIOContext(ctx);
      assertEquals(err.getClass(), java.net.ConnectException.class);
      testComplete();
    }).end();
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
    client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      testComplete();
    }).exceptionHandler(err -> {
      fail();
    }).end();
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
    startServer();
    client.post(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      fail();
    }).exceptionHandler(err -> {
      Context ctx = Vertx.currentContext();
      assertOnIOContext(ctx);
      assertTrue(err instanceof StreamResetException);
      StreamResetException reset = (StreamResetException) err;
      assertEquals(8, reset.getCode());
      testComplete();
    }).setChunked(true).write(chunk);
    await();
  }

  @Test
  public void testServerResetClientStreamDuringResponse() throws Exception {
    waitFor(2);
    String chunk = TestUtils.randomAlphaString(1024);
    Future<Void> doReset = Future.future();
    server.requestHandler(req -> {
      doReset.setHandler(onSuccess(v -> {
        req.response().reset(8);
      }));
      req.response().setChunked(true).write(Buffer.buffer(chunk));
    });
    startServer();
    Context ctx = vertx.getOrCreateContext();
    Handler<Throwable> resetHandler = err -> {
      assertOnIOContext(ctx);
      assertTrue(err instanceof StreamResetException);
      StreamResetException reset = (StreamResetException) err;
      assertEquals(8, reset.getCode());
      complete();
    };
    ctx.runOnContext(v -> {
      client.post(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
        resp.exceptionHandler(resetHandler);
        resp.handler(buff -> {
          doReset.complete();
        });
      }).exceptionHandler(resetHandler).setChunked(true).write(chunk);
    });
    await();
  }

  @Test
  public void testClientResetServerStreamDuringRequest() throws Exception {
    Future<Void> bufReceived = Future.future();
    server.requestHandler(req -> {
      req.handler(buf -> {
        bufReceived.complete();
      });
      req.exceptionHandler(err -> {
        assertTrue(err instanceof StreamResetException);
      });
      req.response().exceptionHandler(err -> {
        assertTrue(err instanceof StreamResetException);
        assertEquals(10L, ((StreamResetException) err).getCode());
        testComplete();
      });
    });
    startServer();
    HttpClientRequest req = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      fail();
    }).setChunked(true).write(Buffer.buffer("hello"));
    bufReceived.setHandler(ar -> {
      req.reset(10);
    });
    await();
  }

  @Test
  public void testClientResetServerStreamDuringResponse() throws Exception {
    server.requestHandler(req -> {
      req.exceptionHandler(err -> {
        assertTrue(err instanceof StreamResetException);
      });
      req.response().exceptionHandler(err -> {
        assertTrue(err instanceof StreamResetException);
        assertEquals(10L, ((StreamResetException) err).getCode());
        testComplete();
      });
      req.response().setChunked(true).write(Buffer.buffer("some-data"));
    });
    startServer();
    HttpClientRequest req = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath");
    req.handler(resp -> {
      resp.exceptionHandler(this::fail);
      req.reset(10);
      assertIllegalStateException(() -> req.write(Buffer.buffer()));
      assertIllegalStateException(req::end);
    }).setChunked(true).write(Buffer.buffer("hello"));
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
    startServer();
    AtomicReference<Context> ctx = new AtomicReference<>();
    HttpClientRequest req = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      Context current = Vertx.currentContext();
      if (ctx.get() == null) {
        ctx.set(current);
      } else {
        assertEquals(ctx.get(), current);
      }
      resp.endHandler(v -> {
        complete();
      });
    });
    req.pushHandler(pushedReq -> {
      Context current = Vertx.currentContext();
      if (ctx.get() == null) {
        ctx.set(current);
      } else {
        assertEquals(ctx.get(), current);
      }
      assertOnIOContext(current);
      assertEquals(HttpMethod.GET, pushedReq.method());
      assertEquals("/wibble?a=b", pushedReq.uri());
      assertEquals("/wibble", pushedReq.path());
      assertEquals("a=b", pushedReq.query());
      pushedReq.handler(resp -> {
        assertEquals(200, resp.statusCode());
        Buffer content = Buffer.buffer();
        resp.handler(content::appendBuffer);
        resp.endHandler(v -> {
          complete();
        });
      });
    });
    req.end();
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
    startServer();
    HttpClientRequest req = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      fail();
    });
    req.pushHandler(pushedReq -> {
      pushedReq.handler(pushedResp -> {
        pushedResp.handler(buff -> {
          pushedReq.reset(Http2Error.CANCEL.code());
        });
      });
    });
    req.end();
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
    startServer();
    client.close();
    client = vertx.createHttpClient(clientOptions.setInitialSettings(new io.vertx.core.http.Http2Settings().setMaxConcurrentStreams(0L)));
    HttpClientRequest req = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      fail();
    });
    req.pushHandler(pushedReq -> {
      pushedReq.reset(Http2Error.CANCEL.code());
    });
    req.end();
    await();
  }

  @Test
  public void testResetPushPromiseNoHandler() throws Exception {
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/wibble", ar -> {
        assertTrue(ar.succeeded());
        HttpServerResponse resp = ar.result();
        resp.setChunked(true).write("content");
        resp.exceptionHandler(err -> {
          assertTrue(err instanceof StreamResetException);
          assertEquals(0, ((StreamResetException) err).getCode());
          testComplete();
        });
      });
    });
    startServer();
    HttpClientRequest req = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
    });
    req.end();
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
    HttpClientRequest req1 = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath");
    req1.connectionHandler(conn -> {
      Context ctx = Vertx.currentContext();
      assertOnIOContext(ctx);
      assertTrue(connection.compareAndSet(null, conn));
    });
    req1.handler(resp -> {
      assertSame(connection.get(), req1.connection());
      complete();
    });
    HttpClientRequest req2 = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath");
    req2.connectionHandler(conn -> {
      fail();
    });
    req2.handler(resp -> {
      assertSame(connection.get(), req1.connection());
      complete();
    });
    req1.end();
    req2.end();
    await();
  }

  @Test
  public void testConnectionShutdownInConnectionHandler() throws Exception {
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
      req.response().end();
    });
    startServer();
    AtomicInteger clientStatus = new AtomicInteger();
    HttpClientRequest req1 = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath");
    req1.connectionHandler(conn -> {
      Context ctx = Vertx.currentContext();
      conn.shutdownHandler(v -> {
        assertOnIOContext(ctx);
        clientStatus.compareAndSet(1, 2);
      });
      if (clientStatus.getAndIncrement() == 0) {
        conn.shutdown();
      }
    });
    req1.exceptionHandler(err -> {
      fail();
    });
    req1.handler(resp -> {
      assertEquals(2, clientStatus.getAndIncrement());
      resp.endHandler(v -> {
        testComplete();
      });
    });
    req1.end();
    await();
  }

  @Test
  public void testServerShutdownConnection() throws Exception {
    waitFor(2);
    server.connectionHandler(HttpConnection::shutdown);
    server.requestHandler(req -> fail());
    startServer();
    HttpClientRequest req1 = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath");
    req1.connectionHandler(conn -> {
      Context ctx = Vertx.currentContext();
      conn.goAwayHandler(ga -> {
        assertOnIOContext(ctx);
        complete();
      });
    });
    AtomicInteger count = new AtomicInteger();
    req1.exceptionHandler(err -> {
      if (count.getAndIncrement() == 0) {
        complete();
      }
    });
    req1.handler(resp -> {
      fail();
    });
    req1.end();
    await();
  }

  @Test
  public void testReceivingGoAwayDiscardsTheConnection() throws Exception {
    AtomicInteger reqCount = new AtomicInteger();
    server.requestHandler(req -> {
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
    startServer();
    HttpClientRequest req1 = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath");
    req1.connectionHandler(conn -> {
      AtomicInteger gaCount = new AtomicInteger();
      conn.goAwayHandler(ga -> {
        if (gaCount.getAndIncrement() == 0) {
          client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp2 -> {
            testComplete();
          }).setTimeout(5000).exceptionHandler(this::fail).end();
        }
      });
    });
    req1.handler(resp1 -> {
      fail();
    }).end();
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
    HttpClientRequest req1 = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath");
    req1.handler(resp1 -> {
      req1.connection().goAway(0);
      client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp2 -> {
        testComplete();
      }).setTimeout(5000).exceptionHandler(this::fail).end();
    }).end();
    await();
  }

  private Http2ConnectionHandler createHttpConnectionHandler(BiFunction<Http2ConnectionDecoder, Http2ConnectionEncoder, Http2FrameListener> handler) {

    class Handler extends Http2ConnectionHandler {
      public Handler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) {
        super(decoder, encoder, initialSettings);
        decoder.frameListener(handler.apply(decoder, encoder));
      }
    }

    class Builder extends AbstractHttp2ConnectionHandlerBuilder<Handler, Builder> {
      @Override
      protected Handler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) throws Exception {
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

  private ServerBootstrap createServer(BiFunction<Http2ConnectionDecoder, Http2ConnectionEncoder, Http2FrameListener> handler) {
    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.channel(NioServerSocketChannel.class);
    bootstrap.group(new NioEventLoopGroup());
    bootstrap.childHandler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        SSLHelper sslHelper = new SSLHelper(serverOptions, KeyStoreHelper.create((VertxInternal) vertx, TLSCert.JKS.getServerKeyCertOptions()), null);
        SslHandler sslHandler = sslHelper.setApplicationProtocols(Arrays.asList(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1)).createSslHandler((VertxInternal) vertx, DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT);
        ch.pipeline().addLast(sslHandler);
        ch.pipeline().addLast(new ApplicationProtocolNegotiationHandler("whatever") {
          @Override
          protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
            if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
              ChannelPipeline p = ctx.pipeline();
              createHttpConnectionHandler(handler);
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

  private ServerBootstrap createClearTextServer(BiFunction<Http2ConnectionDecoder, Http2ConnectionEncoder, Http2FrameListener> handler) {
    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.channel(NioServerSocketChannel.class);
    bootstrap.group(new NioEventLoopGroup());
    bootstrap.childHandler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        HttpServerCodec sourceCodec = new HttpServerCodec();
        HttpServerUpgradeHandler.UpgradeCodecFactory upgradeCodecFactory = protocol -> {
          if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
            return new Http2ServerUpgradeCodec(createHttpConnectionHandler(handler));
          } else {
            return null;
          }
        };
        ch.pipeline().addLast(sourceCodec);
        ch.pipeline().addLast(new HttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory));
      }
    });
    return bootstrap;
  }

  @Test
  public void testStreamError() throws Exception {
    waitFor(2);
    ServerBootstrap bootstrap = createServer((dec, enc) -> new Http2EventAdapter() {
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
      Context ctx = vertx.getOrCreateContext();
      ctx.runOnContext(v -> {
        client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
          resp.exceptionHandler(err -> {
            assertOnIOContext(ctx);
            if (err instanceof Http2Exception.StreamException) {
              complete();
            }
          });
        }).connectionHandler(conn -> {
          conn.exceptionHandler(err -> {
            fail();
          });
        }).exceptionHandler(err -> {
          assertOnIOContext(ctx);
          if (err instanceof Http2Exception.StreamException) {
            complete();
          }
        }).sendHead();
      });
      await();
    } finally {
      s.channel().close().sync();
    }
  }

  @Test
  public void testConnectionDecodeError() throws Exception {
    waitFor(3);
    ServerBootstrap bootstrap = createServer((dec, enc) -> new Http2EventAdapter() {
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
      ctx.runOnContext(v -> {
        client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
          resp.exceptionHandler(err -> {
            assertOnIOContext(ctx);
            if (err instanceof Http2Exception) {
              complete();
            }
          });
        }).connectionHandler(conn -> {
          conn.exceptionHandler(err -> {
            assertSame(ctx, Vertx.currentContext());
            if (err instanceof Http2Exception) {
              complete();
            }
          });
        }).exceptionHandler(err -> {
          assertOnIOContext(ctx);
          if (err instanceof Http2Exception) {
            complete();
          }
        }).sendHead();
      });
      await();
    } finally {
      s.channel().close().sync();
    }
  }

  @Test
  public void testInvalidServerResponse() throws Exception {
    ServerBootstrap bootstrap = createServer((dec, enc) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        enc.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("xyz"), 0, false, ctx.newPromise());
        ctx.flush();
      }
    });
    ChannelFuture s = bootstrap.bind(DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT).sync();
    try {
      Context ctx = vertx.getOrCreateContext();
      ctx.runOnContext(v -> {
        client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
          fail();
        }).connectionHandler(conn -> {
          conn.exceptionHandler(err -> {
            fail();
          });
        }).exceptionHandler(err -> {
          assertOnIOContext(ctx);
          if (err instanceof NumberFormatException) {
            testComplete();
          }
        }).end();
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
    server.close();
    server = vertx.createHttpServer(serverOptions);
    server.requestHandler(req -> {
      assertEquals(enabled ? "deflate, gzip" : null, req.getHeader(HttpHeaderNames.ACCEPT_ENCODING));
      req.response().putHeader(HttpHeaderNames.CONTENT_ENCODING.toLowerCase(), "gzip").end(Buffer.buffer(compressed));
    });
    startServer();
    client.close();
    client = vertx.createHttpClient(clientOptions.setTryUseCompression(enabled));
    client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      String encoding = resp.getHeader(HttpHeaderNames.CONTENT_ENCODING);
      assertEquals(enabled ? null : "gzip", encoding);
      resp.bodyHandler(buff -> {
        assertEquals(Buffer.buffer(enabled ? expected : compressed), buff);
        testComplete();
      });
    }).end();
    await();
  }

  @Test
  public void test100Continue() throws Exception {
    AtomicInteger status = new AtomicInteger();
    server.close();
    server = vertx.createHttpServer(serverOptions.setHandle100ContinueAutomatically(true));
    server.requestHandler(req -> {
      assertEquals(0, status.getAndIncrement());
      HttpServerResponse resp = req.response();
      req.bodyHandler(body -> {
        assertEquals(2, status.getAndIncrement());
        assertEquals("request-body", body.toString());
        resp.putHeader("wibble", "wibble-value").end("response-body");
      });
    });
    startServer();
    HttpClientRequest req = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      assertEquals(3, status.getAndIncrement());
      resp.bodyHandler(body -> {
        assertEquals(4, status.getAndIncrement());
        assertEquals("response-body", body.toString());
        testComplete();
      });
    });
    req.putHeader("expect", "100-continue");
    req.continueHandler(v -> {
      Context ctx = Vertx.currentContext();
      assertOnIOContext(ctx);
      assertEquals(1, status.getAndIncrement());
      req.end(Buffer.buffer("request-body"));
    });
    req.sendHead(version -> {
      assertEquals(3, req.streamId());
    });
    await();
  }

  @Test
  public void testNetSocketConnect() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      NetSocket socket = req.netSocket();
      AtomicInteger status = new AtomicInteger();
      socket.handler(buff -> {
        switch (status.getAndIncrement()) {
          case 0:
            assertEquals(Buffer.buffer("some-data"), buff);
            socket.write(buff);
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
        socket.write(Buffer.buffer("last-data"));
      });
      socket.closeHandler(v -> {
        assertEquals(3, status.getAndIncrement());
        complete();
      });
    });
    startServer();
    client.request(HttpMethod.CONNECT, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
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
    }).setHost("whatever.com").sendHead();
    await();
  }

  @Test
  public void testServerCloseNetSocket() throws Exception {
    waitFor(2);
    AtomicInteger status = new AtomicInteger();
    server.requestHandler(req -> {
      NetSocket socket = req.netSocket();
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
    });

    startServer();
    client.request(HttpMethod.CONNECT, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
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
    }).setHost("whatever.com").sendHead();
    await();
  }

  @Test
  public void testSendHeadersCompletionHandler() throws Exception {
    AtomicInteger status = new AtomicInteger();
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer();
    HttpClientRequest req = client.request(HttpMethod.CONNECT, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      assertEquals(1, status.getAndIncrement());
      resp.endHandler(v -> {
        assertEquals(3, status.getAndIncrement());
        testComplete();
      });
    });
    req.endHandler(v -> {
      assertEquals(2, status.getAndIncrement());
    });
    req.sendHead(version -> {
      assertEquals(0, status.getAndIncrement());
      assertSame(HttpVersion.HTTP_2, version);
      req.end();
    });
    await();
  }

  @Test
  public void testUnknownFrame() throws Exception {
    Buffer expectedSend = TestUtils.randomBuffer(500);
    Buffer expectedRecv = TestUtils.randomBuffer(500);
    server.requestHandler(req -> {
      req.unknownFrameHandler(frame -> {
        assertEquals(10, frame.type());
        assertEquals(253, frame.flags());
        assertEquals(expectedSend, frame.payload());
        req.response().writeFrame(12, 134, expectedRecv).end();
      });
    });
    startServer();
    AtomicInteger status = new AtomicInteger();
    HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      Context ctx = Vertx.currentContext();
      assertEquals(0, status.getAndIncrement());
      resp.unknownFrameHandler(frame -> {
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
    });
    req.sendHead(version -> {
      assertSame(HttpVersion.HTTP_2, version);
      req.writeFrame(10, 253, expectedSend);
      req.end();
    });
    await();
  }

  @Test
  public void testUpgradeToClearText() throws Exception {
    ServerBootstrap bootstrap = createClearTextServer((dec, enc) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        enc.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("200"), 0, true, ctx.newPromise());
        ctx.flush();
      }
    });
    ChannelFuture s = bootstrap.bind(DEFAULT_HTTP_HOST, DEFAULT_HTTP_PORT).sync();
    try {
      client.close();
      client = vertx.createHttpClient(clientOptions.setUseAlpn(false));
      client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
        assertEquals(HttpVersion.HTTP_2, resp.version());
        testComplete();
      }).exceptionHandler(this::fail).end();
      await();
    } finally {
      s.channel().close().sync();
    }
  }

  @Test
  public void testRejectUpgradeToClearText() throws Exception {
    System.setProperty("vertx.disableH2c", "true");
    try {
      server.close();
      server = vertx.createHttpServer(serverOptions.setUseAlpn(false).setSsl(false).setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT));
      server.requestHandler(req -> {
        assertEquals(HttpVersion.HTTP_1_1, req.version());
        req.response().end();
      });
      startServer();
      client.close();
      client = vertx.createHttpClient(clientOptions.setUseAlpn(false));
      client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
        assertEquals(HttpVersion.HTTP_1_1, resp.version());
        testComplete();
      }).exceptionHandler(this::fail).end();
      await();
    } finally {
      System.clearProperty("vertx.disableH2c");
    }
  }

  @Test
  public void testIdleTimeout() throws Exception {
    waitFor(4);
    server.requestHandler(req -> {
      req.connection().closeHandler(v -> {
        complete();
      });
      req.response().setChunked(true).write("somedata");
    });
    startServer();
    client.close();
    client = vertx.createHttpClient(clientOptions.setIdleTimeout(2));
    HttpClientRequest req = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      Context ctx = Vertx.currentContext();
      resp.exceptionHandler(err -> {
        assertOnIOContext(ctx);
        complete();
      });
    });
    req.exceptionHandler(err -> {
      complete();
    });
    req.connectionHandler(conn -> {
      conn.closeHandler(v -> {
        Context ctx = Vertx.currentContext();
        assertOnIOContext(ctx);
        complete();
      });
    });
    req.sendHead();
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
    startServer();
    client.close();
    client = vertx.createHttpClient(clientOptions.setIdleTimeout(2));
    HttpClientRequest req = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      resp.exceptionHandler(err -> {
        fail();
      });
      resp.endHandler(v -> {
        time.set(System.currentTimeMillis());
        complete();
      });
    });
    req.exceptionHandler(err -> {
      fail();
    });
    req.connectionHandler(conn -> {
      conn.closeHandler(v -> {
        assertTrue(System.currentTimeMillis() - time.get() > 1000);
        complete();
      });
    });
    req.end();
    await();
  }

  @Test
  public void testSendPing() throws Exception {
    waitFor(2);
    Buffer expected = TestUtils.randomBuffer(8);
    Context ctx = vertx.getOrCreateContext();
    server.close();
    server.connectionHandler(conn -> {
      conn.pingHandler(data -> {
        assertEquals(expected, data);
        complete();
      });
    });
    server.requestHandler(req -> {});
    startServer(ctx);
    HttpClientRequest req = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {

    });
    req.connectionHandler(conn -> {
      conn.ping(expected, ar -> {
        assertTrue(ar.succeeded());
        Buffer buff = ar.result();
        assertEquals(expected, buff);
        complete();
      });
    });
    req.end();
    await();
  }

  @Test
  public void testReceivePing() throws Exception {
    Buffer expected = TestUtils.randomBuffer(8);
    Context ctx = vertx.getOrCreateContext();
    server.close();
    server.connectionHandler(conn -> {
      conn.ping(expected, ar -> {

      });
    });
    server.requestHandler(req -> {});
    startServer(ctx);
    HttpClientRequest req = client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {

    });
    req.connectionHandler(conn -> {
      conn.pingHandler(data -> {
        assertEquals(expected, data);
        complete();
      });
    });
    req.end();
    await();
  }
}
