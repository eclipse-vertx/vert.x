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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.KeyStoreHelper;
import io.vertx.core.net.impl.SSLHelper;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ClientTest extends Http2TestBase {

  HttpClientOptions clientOptions;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    clientOptions = new HttpClientOptions().
        setUseAlpn(true).
        setTrustStoreOptions((JksOptions) getClientTrustOptions(Trust.JKS)).
        setProtocolVersion(HttpVersion.HTTP_2);
    client = vertx.createHttpClient(clientOptions);
  }

  @Test
  public void testClientSettings() throws Exception {
    Http2Settings initialSettings = randomSettings();
//    Http2Settings updatedSettings = randomSettings();
//    Future<Void> settingsRead = Future.future();
    server.requestHandler(req -> {
      io.vertx.core.http.Http2Settings settings = req.connection().remoteSettings();
      assertEquals(initialSettings.maxHeaderListSize(), settings.getMaxHeaderListSize());
      assertEquals(initialSettings.maxFrameSize(), settings.getMaxFrameSize());
      assertEquals(initialSettings.initialWindowSize(), settings.getInitialWindowSize());
      assertEquals(initialSettings.maxConcurrentStreams(), settings.getMaxConcurrentStreams());
      assertEquals((long) initialSettings.headerTableSize(), (long) settings.getHeaderTableSize());
/*
      req.connection().clientSettingsHandler(update -> {
        assertOnIOContext(ctx);
        assertEquals(updatedSettings.maxHeaderListSize(), update.getMaxHeaderListSize());
        assertEquals(updatedSettings.maxFrameSize(), update.getMaxFrameSize());
        assertEquals(updatedSettings.initialWindowSize(), update.getInitialWindowSize());
        assertEquals(updatedSettings.maxConcurrentStreams(), update.getMaxConcurrentStreams());
        assertEquals((long) updatedSettings.headerTableSize(), (long) update.getHeaderTableSize());
        testComplete();
      });
      settingsRead.complete();
*/
      req.response().end();
    });
    startServer();
    client.close();
    client = vertx.createHttpClient(clientOptions.setHttp2Settings(new io.vertx.core.http.Http2Settings().
        setEnablePush(initialSettings.pushEnabled()).
        setHeaderTableSize((int)(long)initialSettings.headerTableSize()).
        setInitialWindowSize(initialSettings.initialWindowSize()).
        setMaxConcurrentStreams(initialSettings.maxConcurrentStreams()).
        setMaxFrameSize(initialSettings.maxFrameSize()).
        setMaxHeaderListSize(initialSettings.maxHeaderListSize())));
    client.getNow(4043, "localhost", "/somepath", resp -> {
      testComplete();
    });
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
      assertEquals("localhost:4043", req.host());
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
    client.get(4043, "localhost", "/somepath", resp -> {
      assertEquals(1, reqCount.get());
      assertEquals(HttpVersion.HTTP_2, resp.version());
      assertEquals("text/plain", resp.getHeader("content-type"));
      assertEquals("200", resp.getHeader(":status"));
      assertEquals("foo_value", resp.getHeader("foo_response"));
      assertEquals("bar_value", resp.getHeader("bar_response"));
      assertEquals(2, resp.headers().getAll("juu_response").size());
      assertEquals("juu_value_1", resp.headers().getAll("juu_response").get(0));
      assertEquals("juu_value_2", resp.headers().getAll("juu_response").get(1));
      Buffer content = Buffer.buffer();
      resp.handler(content::appendBuffer);
      resp.endHandler(v -> {
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
  public void testOverrideAuthority() throws Exception {
    server.requestHandler(req -> {
      assertEquals("localhost:4444", req.host());
      req.response().end();
    });
    startServer();
    client.get(4043, "localhost", "/somepath", resp -> {
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
    client.getNow(4043, "localhost", "/somepeth", resp -> {
      assertEquals(null, resp.getTrailer("foo"));
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
    client.getNow(4043, "localhost", "/somepath", resp -> {
      resp.bodyHandler(body -> {
        assertEquals(expected, body);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testPost() throws Exception {
    Buffer content = Buffer.buffer();
    String expected = TestUtils.randomAlphaString(100);
    server.requestHandler(req -> {
      assertEquals(HttpMethod.POST, req.method());
      req.handler(content::appendBuffer);
      req.endHandler(v -> {
        req.response().end();
      });
    });
    startServer();
    client.post(4043, "localhost", "/somepath", resp -> {
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
    HttpClientRequest req = client.post(4043, "localhost", "/somepath", resp -> {
      testComplete();
    }).setChunked(true).exceptionHandler(err -> {
      fail();
    });
    AtomicInteger count = new AtomicInteger();
    vertx.setPeriodic(1, timerID -> {
      if (req.writeQueueFull()) {
        assertTrue(paused.get());
        assertEquals(1, numPause.get());
        req.drainHandler(v -> {
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
    client.getNow(4043, "localhost", "/somepath", resp -> {
      Buffer received = Buffer.buffer();
      resp.pause();
      resp.handler(received::appendBuffer);
      resp.endHandler(v -> {
        assertEquals(expected.toString(), received.toString());
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
      server = vertx.createHttpServer(serverOptions.setHttp2Settings(new io.vertx.core.http.Http2Settings().setMaxConcurrentStreams(10L)));
    }
    server.requestHandler(req -> {
      req.response().end(expected);
    });
    startServer();
    CountDownLatch latch = new CountDownLatch(1);
    client.get(4043, "localhost", "/somepath", resp -> {
    }).connectionHandler(conn -> {
      conn.remoteSettingsHandler(settings -> {
        assertEquals(max, settings.getMaxConcurrentStreams());
        latch.countDown();
      });
    }).exceptionHandler(err -> {
      fail();
    }).end();
    awaitLatch(latch);
    for (int i = 0;i < numReq;i++) {
      client.get(4043, "localhost", "/somepath", resp -> {
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
    client.get(4043, "localhost", "/somepath", resp -> {
      resp.endHandler(v -> {
        doReq.countDown();
      });
    }).exceptionHandler(err -> {
      fail();
    }).end();
    awaitLatch(doReq);
    client.get(4043, "localhost", "/somepath", resp -> {
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
    client.get(4044, "localhost", "/somepath", resp -> {
    }).exceptionHandler(err -> {
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
    client.get(4043, "localhost", "/somepath", resp -> {
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
    client.post(4043, "localhost", "/somepath", resp -> {
      fail();
    }).exceptionHandler(err -> {
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
      client.post(4043, "localhost", "/somepath", resp -> {
        resp.exceptionHandler(resetHandler);
        resp.handler(buff -> {
          doReset.complete();
        });
      }).exceptionHandler(resetHandler).setChunked(true).write(chunk);
    });
    await();
  }

  @Test
  public void testClientResetServerStream() throws Exception {
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
    HttpClientRequest req = client.get(4043, "localhost", "/somepath", resp -> {
      fail();
    }).setChunked(true).write(Buffer.buffer("hello"));
    bufReceived.setHandler(ar -> {
      req.reset(10);
    });
    await();
  }

  @Test
  public void testPushPromise() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().pushPromise(HttpMethod.GET, "/wibble", ar -> {
        assertTrue(ar.succeeded());
        HttpServerResponse response = ar.result();
        response.end("the_content");
      }).end();
    });
    startServer();
    HttpClientRequest req = client.get(4043, "localhost", "/somepath", resp -> {
      resp.endHandler(v -> {
        complete();
      });
    });
    req.pushPromiseHandler(pushedReq -> {
      assertEquals(HttpMethod.GET, pushedReq.method());
      assertEquals("/wibble", pushedReq.uri());
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
      req.response().pushPromise(HttpMethod.GET, "/wibble", ar -> {
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
    HttpClientRequest req = client.get(4043, "localhost", "/somepath", resp -> {
      fail();
    });
    req.pushPromiseHandler(pushedReq -> {
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
      req.response().pushPromise(HttpMethod.GET, "/wibble", ar -> {
        assertFalse(ar.succeeded());
        testComplete();
      });
    });
    startServer();
    client.close();
    client = vertx.createHttpClient(clientOptions.setHttp2Settings(new io.vertx.core.http.Http2Settings().setMaxConcurrentStreams(0L)));
    HttpClientRequest req = client.get(4043, "localhost", "/somepath", resp -> {
      fail();
    });
    req.pushPromiseHandler(pushedReq -> {
      pushedReq.reset(Http2Error.CANCEL.code());
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
    HttpClientRequest req1 = client.get(4043, "localhost", "/somepath");
    req1.connectionHandler(conn -> {
      assertTrue(connection.compareAndSet(null, conn));
    });
    req1.handler(resp -> {
      assertSame(connection.get(), req1.connection());
      complete();
    });
    HttpClientRequest req2 = client.get(4043, "localhost", "/somepath");
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
    HttpClientRequest req1 = client.get(4043, "localhost", "/somepath");
    req1.connectionHandler(conn -> {
      conn.shutdownHandler(v -> {
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
    HttpClientRequest req1 = client.get(4043, "localhost", "/somepath");
    req1.connectionHandler(conn -> {
      conn.goAwayHandler(ga -> {
        complete();
      });
    });
    req1.exceptionHandler(err -> {
      complete();
    });
    req1.handler(resp -> {
      fail();
    });
    req1.end();
    await();
  }

  private ServerBootstrap createServer(BiFunction<Http2ConnectionDecoder, Http2ConnectionEncoder, Http2FrameListener> handler) {
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
    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.channel(NioServerSocketChannel.class);
    bootstrap.group(new NioEventLoopGroup());
    bootstrap.childHandler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        SSLHelper sslHelper = new SSLHelper(serverOptions, KeyStoreHelper.create((VertxInternal) vertx, getServerCertOptions(KeyCert.JKS)), null);
        SslHandler sslHandler = sslHelper.createSslHandler((VertxInternal) vertx, false, "localhost", 4043);
        ch.pipeline().addLast(sslHandler);
        ch.pipeline().addLast(new ApplicationProtocolNegotiationHandler("whatever") {
          @Override
          protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
            if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
              ChannelPipeline p = ctx.pipeline();
              Builder builder = new Builder();
              Handler clientHandler = builder.build();
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
    ChannelFuture s = bootstrap.bind("localhost", 4043).sync();
    try {
      Context ctx = vertx.getOrCreateContext();
      ctx.runOnContext(v -> {
        client.get(4043, "localhost", "/somepath", resp -> {
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
        }).end();
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
    ChannelFuture s = bootstrap.bind("localhost", 4043).sync();
    try {
      Context ctx = vertx.getOrCreateContext();
      ctx.runOnContext(v -> {
        client.get(4043, "localhost", "/somepath", resp -> {
          resp.exceptionHandler(err -> {
            assertOnIOContext(ctx);
            if (err instanceof Http2Exception) {
              complete();
            }
          });
        }).connectionHandler(conn -> {
          conn.exceptionHandler(err -> {
            assertOnIOContext(ctx);
            if (err instanceof Http2Exception) {
              complete();
            }
          });
        }).exceptionHandler(err -> {
          assertOnIOContext(ctx);
          if (err instanceof Http2Exception) {
            complete();
          }
        }).end();
      });
      await();
    } finally {
      s.channel().close().sync();
    }
  }
}
