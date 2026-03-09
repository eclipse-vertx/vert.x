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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
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
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.http.impl.tcp.Http1xOrH2CHandler;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.impl.Utils;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.test.core.DetectFileDescriptorLeaks;
import io.vertx.test.core.TestUtils;
import io.vertx.test.tls.Trust;
import junit.framework.AssertionFailedError;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static io.vertx.test.core.TestUtils.assertIllegalStateException;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ServerTest extends Http2TestBase {

  private static Http2Headers headers(String method, String scheme, String path) {
    return new DefaultHttp2Headers().method(method).scheme(scheme).path(path);
  }

  private static Http2Headers GET(String scheme, String path) {
    return headers("GET", scheme, path);
  }

  private static Http2Headers GET(String path) {
    return headers("GET", "https", path);
  }

  private static Http2Headers POST(String path) {
    return headers("POST", "https", path);
  }

  class TestClient {

    final Http2Settings settings = new Http2Settings();

    public class Connection {
      public final Channel channel;
      public final ChannelHandlerContext context;
      public final Http2Connection connection;
      public final Http2ConnectionEncoder encoder;
      public final Http2ConnectionDecoder decoder;

      public Connection(ChannelHandlerContext context, Http2Connection connection, Http2ConnectionEncoder encoder, Http2ConnectionDecoder decoder) {
        this.channel = context.channel();
        this.context = context;
        this.connection = connection;
        this.encoder = encoder;
        this.decoder = decoder;
      }

      public int nextStreamId() {
        return connection.local().incrementAndGetNextStreamId();
      }
    }

    class TestClientHandler extends Http2ConnectionHandler {

      private final Consumer<Connection> requestHandler;
      private boolean handled;

      public TestClientHandler(
          Consumer<Connection> requestHandler,
          Http2ConnectionDecoder decoder,
          Http2ConnectionEncoder encoder,
          Http2Settings initialSettings) {
        super(decoder, encoder, initialSettings);
        this.requestHandler = requestHandler;
      }

      @Override
      public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        if (ctx.channel().isActive()) {
          checkHandle(ctx);
        }
      }

      @Override
      public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        checkHandle(ctx);
      }

      private void checkHandle(ChannelHandlerContext ctx) {
        if (!handled) {
          handled = true;
          Connection conn = new Connection(ctx, connection(), encoder(), decoder());
          requestHandler.accept(conn);
        }
      }

      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Ignore
      }
    }

    class TestClientHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<TestClientHandler, TestClientHandlerBuilder> {

      private final Consumer<Connection> requestHandler;

      public TestClientHandlerBuilder(Consumer<Connection> requestHandler) {
        this.requestHandler = requestHandler;
      }

      @Override
      protected TestClientHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) throws Exception {
        return new TestClientHandler(requestHandler, decoder, encoder, initialSettings);
      }

      public TestClientHandler build(Http2Connection conn) {
        connection(conn);
        initialSettings(settings);
        frameListener(new Http2EventAdapter() {
          @Override
          public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
            return super.onDataRead(ctx, streamId, data, padding, endOfStream);
          }
        });
        return super.build();
      }
    }

    protected ChannelInitializer channelInitializer(int port, String host, Promise<SslHandshakeCompletionEvent> latch, Consumer<Connection> handler) {
      return new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel ch) throws Exception {
          SslContext sslContext = SslContextBuilder
            .forClient()
            .applicationProtocolConfig(new ApplicationProtocolConfig(
              ApplicationProtocolConfig.Protocol.ALPN,
              ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
              ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
              HttpVersion.HTTP_2.alpnName(), HttpVersion.HTTP_1_1.alpnName()
            )).trustManager(Trust.SERVER_JKS.get().getTrustManagerFactory(vertx))
            .build();
          SslHandler sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, host, port);
          ch.pipeline().addLast(sslHandler);
          ch.pipeline().addLast(new ApplicationProtocolNegotiationHandler("whatever") {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
              if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                ChannelPipeline p = ctx.pipeline();
                Http2Connection connection = new DefaultHttp2Connection(false);
                TestClientHandlerBuilder clientHandlerBuilder = new TestClientHandlerBuilder(handler);
                TestClientHandler clientHandler = clientHandlerBuilder.build(connection);
                p.addLast(clientHandler);
                return;
              }
              ctx.close();
              throw new IllegalStateException("unknown protocol: " + protocol);
            }
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
              if (evt instanceof SslHandshakeCompletionEvent) {
                SslHandshakeCompletionEvent handshakeCompletion = (SslHandshakeCompletionEvent) evt;
                latch.tryComplete(handshakeCompletion);
              }
              super.userEventTriggered(ctx, evt);
            }
            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
              latch.tryFail("Channel closed");
              super.channelInactive(ctx);
            }
          });
        }
      };
    }

    public Channel connect(int port, String host, Consumer<Connection> handler) throws Exception {
      Bootstrap bootstrap = new Bootstrap();
      NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
      eventLoopGroups.add(eventLoopGroup);
      bootstrap.channel(NioSocketChannel.class);
      bootstrap.group(eventLoopGroup);
      Promise<SslHandshakeCompletionEvent> promise = Promise.promise();
      bootstrap.handler(channelInitializer(port, host, promise, handler));
      ChannelFuture fut = bootstrap.connect(new InetSocketAddress(host, port));
      fut.sync();
      SslHandshakeCompletionEvent completion = promise.future().toCompletionStage().toCompletableFuture().get();
      if (completion.isSuccess()) {
        return fut.channel();
      } else {
        eventLoopGroup.shutdownGracefully();
        AssertionFailedError afe = new AssertionFailedError();
        afe.initCause(completion.cause());
        throw afe;
      }
    }
  }

  public Http2ServerTest() {
    super(ReportMode.FORBIDDEN);
  }

  @Test
  public void testConnectionHandler() throws Exception {
    waitFor(2);
    Context ctx = vertx.getOrCreateContext();
    server.connectionHandler(conn -> {
      Assert.assertTrue(Context.isOnEventLoopThread());
      TestUtils.assertSameEventLoop(vertx.getOrCreateContext(), ctx);
      complete();
    });
    server.requestHandler(req -> Assert.fail());
    startServer(ctx);
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      vertx.runOnContext(v -> {
        complete();
      });
    });
    await();
  }

  @Test
  public void testServerInitialSettings() throws Exception {
    io.vertx.core.http.Http2Settings settings = TestUtils.randomHttp2Settings();
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions(serverOptions).setInitialSettings(settings));
    server.requestHandler(req -> Assert.fail());
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2FrameAdapter() {
        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings newSettings) throws Http2Exception {
          vertx.runOnContext(v -> {
            Assert.assertEquals((Long) settings.getHeaderTableSize(), newSettings.headerTableSize());
            Assert.assertEquals((Long) settings.getMaxConcurrentStreams(), newSettings.maxConcurrentStreams());
            Assert.assertEquals((Integer) settings.getInitialWindowSize(), newSettings.initialWindowSize());
            Assert.assertEquals((Integer) settings.getMaxFrameSize(), newSettings.maxFrameSize());
            Assert.assertEquals((Long) settings.getMaxHeaderListSize(), newSettings.maxHeaderListSize());
            Assert.assertEquals(settings.get('\u0007'), newSettings.get('\u0007'));
            testComplete();
          });
        }
      });
    });
    await();
  }

  @Test
  public void testServerSettings() throws Exception {
    waitFor(2);
    io.vertx.core.http.Http2Settings expectedSettings = TestUtils.randomHttp2Settings();
    expectedSettings.setHeaderTableSize((int)io.vertx.core.http.Http2Settings.DEFAULT_HEADER_TABLE_SIZE);
    Context otherContext = vertx.getOrCreateContext();
    server.connectionHandler(conn -> {
      Context ctx = Vertx.currentContext();
      otherContext.runOnContext(v -> {
        conn.updateSettings(expectedSettings).onComplete(TestUtils.onSuccess(ar -> {
          Assert.assertSame(ctx, Vertx.currentContext());
          HttpSettings ackedSettings = conn.settings();
          Assert.assertEquals(expectedSettings.getMaxHeaderListSize(), (long)ackedSettings.getOrDefault(io.vertx.core.http.Http2Settings.MAX_HEADER_LIST_SIZE));
          Assert.assertEquals(expectedSettings.getMaxFrameSize(), (int)ackedSettings.getOrDefault(io.vertx.core.http.Http2Settings.MAX_FRAME_SIZE));
          Assert.assertEquals(expectedSettings.getInitialWindowSize(), (int)ackedSettings.getOrDefault(io.vertx.core.http.Http2Settings.INITIAL_WINDOW_SIZE));
          Assert.assertEquals(expectedSettings.getMaxConcurrentStreams(), (long)ackedSettings.getOrDefault(io.vertx.core.http.Http2Settings.MAX_CONCURRENT_STREAMS));
          Assert.assertEquals(expectedSettings.getHeaderTableSize(),  (long)ackedSettings.getOrDefault(io.vertx.core.http.Http2Settings.HEADER_TABLE_SIZE));
          Assert.assertEquals(expectedSettings.get('\u0007'), ((io.vertx.core.http.Http2Settings)ackedSettings).get(7));
          complete();
        }));
      });
    });
    server.requestHandler(req -> {
      Assert.fail();
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2FrameAdapter() {
        AtomicInteger count = new AtomicInteger();
        Context context = vertx.getOrCreateContext();

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings newSettings) throws Http2Exception {
          context.runOnContext(v -> {
            switch (count.getAndIncrement()) {
              case 0:
                // Initial settings
                break;
              case 1:
                // Server sent settings
                Assert.assertEquals((Long)expectedSettings.getMaxHeaderListSize(), newSettings.maxHeaderListSize());
                Assert.assertEquals((Integer)expectedSettings.getMaxFrameSize(), newSettings.maxFrameSize());
                Assert.assertEquals((Integer)expectedSettings.getInitialWindowSize(), newSettings.initialWindowSize());
                Assert.assertEquals((Long)expectedSettings.getMaxConcurrentStreams(), newSettings.maxConcurrentStreams());
//                assertEquals((Long)expectedSettings.getHeaderTableSize(), newSettings.headerTableSize());
                complete();
                break;
              default:
                Assert.fail();
            }
          });
        }
      });
    });
    await();
  }

  @Test
  public void testClientSettings() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    io.vertx.core.http.Http2Settings initialSettings = TestUtils.randomHttp2Settings();
    io.vertx.core.http.Http2Settings updatedSettings = TestUtils.randomHttp2Settings();
    AtomicInteger count = new AtomicInteger();
    server.connectionHandler(conn -> {
      io.vertx.core.http.HttpSettings settings = conn.remoteSettings();
      Assert.assertEquals(initialSettings.isPushEnabled(), settings.get(io.vertx.core.http.Http2Settings.ENABLE_PUSH));

      // Netty bug ?
      // Nothing has been yet received so we should get Integer.MAX_VALUE
      // assertEquals(Integer.MAX_VALUE, settings.getMaxHeaderListSize());

      Assert.assertEquals(initialSettings.getMaxFrameSize(), (int)settings.get(io.vertx.core.http.Http2Settings.MAX_FRAME_SIZE));
      Assert.assertEquals(initialSettings.getInitialWindowSize(), (int)settings.get(io.vertx.core.http.Http2Settings.INITIAL_WINDOW_SIZE));
      Assert.assertEquals(initialSettings.getMaxConcurrentStreams(), (long)settings.get(io.vertx.core.http.Http2Settings.MAX_CONCURRENT_STREAMS));
      Assert.assertEquals(initialSettings.getHeaderTableSize(), (long)settings.get(io.vertx.core.http.Http2Settings.HEADER_TABLE_SIZE));

      conn.remoteSettingsHandler(update -> {
        TestUtils.assertOnIOContext(ctx);
        switch (count.getAndIncrement()) {
          case 0:
            Assert.assertEquals(updatedSettings.isPushEnabled(), update.get(io.vertx.core.http.Http2Settings.ENABLE_PUSH));
            Assert.assertEquals(updatedSettings.getMaxHeaderListSize(), (long)update.get(io.vertx.core.http.Http2Settings.MAX_HEADER_LIST_SIZE));
            Assert.assertEquals(updatedSettings.getMaxFrameSize(), (int)update.get(io.vertx.core.http.Http2Settings.MAX_FRAME_SIZE));
            Assert.assertEquals(updatedSettings.getInitialWindowSize(), (int)update.get(io.vertx.core.http.Http2Settings.INITIAL_WINDOW_SIZE));
            Assert.assertEquals(updatedSettings.getMaxConcurrentStreams(), (long)update.get(io.vertx.core.http.Http2Settings.MAX_CONCURRENT_STREAMS));
            Assert.assertEquals(updatedSettings.getHeaderTableSize(), (long)update.get(io.vertx.core.http.Http2Settings.HEADER_TABLE_SIZE));
            Assert.assertEquals(updatedSettings.get('\u0007'), ((io.vertx.core.http.Http2Settings)update).get(7));
            testComplete();
            break;
          default:
            Assert.fail();
        }
      });
    });
    server.requestHandler(req -> {
      Assert.fail();
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.settings.putAll(HttpUtils.fromVertxSettings(initialSettings));
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.encoder.writeSettings(request.context, HttpUtils.fromVertxSettings(updatedSettings), request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testGet() throws Exception {
    String expected = TestUtils.randomAlphaString(1000);
    AtomicBoolean requestEnded = new AtomicBoolean();
    Context ctx = vertx.getOrCreateContext();
    AtomicInteger expectedStreamId = new AtomicInteger();
    server.requestHandler(req -> {
      TestUtils.assertOnIOContext(ctx);
      req.endHandler(v -> {
        TestUtils.assertOnIOContext(ctx);
        requestEnded.set(true);
      });
      HttpServerResponse resp = req.response();
      Assert.assertEquals(HttpMethod.GET, req.method());
      Assert.assertEquals(DEFAULT_HTTPS_HOST, req.authority().host());
      Assert.assertEquals(DEFAULT_HTTPS_PORT, req.authority().port());
      Assert.assertEquals("/", req.path());
      Assert.assertTrue(req.isSSL());
      Assert.assertEquals(expectedStreamId.get(), req.streamId());
      Assert.assertEquals("https", req.scheme());
      Assert.assertEquals("/", req.uri());
      Assert.assertEquals("foo_request_value", req.getHeader("Foo_request"));
      Assert.assertEquals("bar_request_value", req.getHeader("bar_request"));
      Assert.assertEquals(2, req.headers().getAll("juu_request").size());
      Assert.assertEquals("juu_request_value_1", req.headers().getAll("juu_request").get(0));
      Assert.assertEquals("juu_request_value_2", req.headers().getAll("juu_request").get(1));
      Assert.assertEquals(Collections.singletonList("cookie_1; cookie_2; cookie_3"), req.headers().getAll("cookie"));
      resp.putHeader("content-type", "text/plain");
      resp.putHeader("Foo_response", "foo_response_value");
      resp.putHeader("bar_response", "bar_response_value");
      resp.putHeader("juu_response", (List<String>)Arrays.asList("juu_response_value_1", "juu_response_value_2"));
      resp.end(expected);
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      expectedStreamId.set(id);
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            Assert.assertEquals(id, streamId);
            Assert.assertEquals("200", headers.status().toString());
            Assert.assertEquals("text/plain", headers.get("content-type").toString());
            Assert.assertEquals("foo_response_value", headers.get("foo_response").toString());
            Assert.assertEquals("bar_response_value", headers.get("bar_response").toString());
            Assert.assertEquals(2, headers.getAll("juu_response").size());
            Assert.assertEquals("juu_response_value_1", headers.getAll("juu_response").get(0).toString());
            Assert.assertEquals("juu_response_value_2", headers.getAll("juu_response").get(1).toString());
            Assert.assertFalse(endStream);
          });
        }
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          String actual = data.toString(StandardCharsets.UTF_8);
          vertx.runOnContext(v -> {
            Assert.assertEquals(id, streamId);
            Assert.assertEquals(expected, actual);
            Assert.assertTrue(endOfStream);
            testComplete();
          });
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
      Http2Headers headers = GET("/").authority(DEFAULT_HTTPS_HOST_AND_PORT);
      headers.set("foo_request", "foo_request_value");
      headers.set("bar_request", "bar_request_value");
      headers.set("juu_request", "juu_request_value_1", "juu_request_value_2");
      headers.set("cookie", Arrays.asList("cookie_1", "cookie_2", "cookie_3"));
      request.encoder.writeHeaders(request.context, id, headers, 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testStatusMessage() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setStatusCode(404);
      Assert.assertEquals("Not Found", resp.getStatusMessage());
      resp.setStatusMessage("whatever");
      Assert.assertEquals("whatever", resp.getStatusMessage());
      testComplete();
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testURI() throws Exception {
    server.requestHandler(req -> {
      Assert.assertEquals("/some/path", req.path());
      Assert.assertEquals("foo=foo_value&bar=bar_value_1&bar=bar_value_2", req.query());
      Assert.assertEquals("/some/path?foo=foo_value&bar=bar_value_1&bar=bar_value_2", req.uri());
      Assert.assertEquals("http://whatever.com/some/path?foo=foo_value&bar=bar_value_1&bar=bar_value_2", req.absoluteURI());
      Assert.assertEquals("whatever.com", req.authority().host());
      MultiMap params = req.params();
      Set<String> names = params.names();
      Assert.assertEquals(2, names.size());
      Assert.assertTrue(names.contains("foo"));
      Assert.assertTrue(names.contains("bar"));
      Assert.assertEquals("foo_value", params.get("foo"));
      Assert.assertEquals(Collections.singletonList("foo_value"), params.getAll("foo"));
      Assert.assertEquals("bar_value_1", params.get("bar"));
      Assert.assertEquals(Arrays.asList("bar_value_1", "bar_value_2"), params.getAll("bar"));
      testComplete();
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2Headers headers = new DefaultHttp2Headers().
          method("GET").
          scheme("http").
          authority("whatever.com").
          path("/some/path?foo=foo_value&bar=bar_value_1&bar=bar_value_2");
      request.encoder.writeHeaders(request.context, id, headers, 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testHeadersEndHandler() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      resp.putHeader("some", "some-header");
      resp.headersEndHandler(v -> {
        TestUtils.assertOnIOContext(ctx);
        Assert.assertFalse(resp.headWritten());
        resp.putHeader("extra", "extra-header");
      });
      resp.write("something");
      Assert.assertTrue(resp.headWritten());
      resp.end();
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            Assert.assertEquals("some-header", headers.get("some").toString());
            Assert.assertEquals("extra-header", headers.get("extra").toString());
            testComplete();
          });
        }
      });
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testBodyEndHandler() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      AtomicInteger count = new AtomicInteger();
      resp.bodyEndHandler(v -> {
        Assert.assertEquals(0, count.getAndIncrement());
        Assert.assertTrue(resp.ended());
      });
      resp.write("something");
      Assert.assertEquals(0, count.get());
      resp.end();
      Assert.assertEquals(1, count.get());
      testComplete();
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testPost() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    Buffer expectedContent = TestUtils.randomBuffer(1000);
    Buffer postContent = Buffer.buffer();
    server.requestHandler(req -> {
      TestUtils.assertOnIOContext(ctx);
      req.handler(buff -> {
        TestUtils.assertOnIOContext(ctx);
        postContent.appendBuffer(buff);
      });
      req.endHandler(v -> {
        TestUtils.assertOnIOContext(ctx);
        req.response().putHeader("content-type", "text/plain").end("");
        Assert.assertEquals(expectedContent, postContent);
        testComplete();
      });
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, POST("/").set("content-type", "text/plain"), 0, false, request.context.newPromise());
      request.encoder.writeData(request.context, id, ((BufferInternal)expectedContent).getByteBuf(), 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testPostFileUpload() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      Buffer tot = Buffer.buffer();
      req.setExpectMultipart(true);
      req.uploadHandler(upload -> {
        TestUtils.assertOnIOContext(ctx);
        Assert.assertEquals("file", upload.name());
        Assert.assertEquals("tmp-0.txt", upload.filename());
        Assert.assertEquals("image/gif", upload.contentType());
        upload.handler(tot::appendBuffer);
        upload.endHandler(v -> {
          Assert.assertEquals(tot, Buffer.buffer("some-content"));
          testComplete();
        });
      });
      req.endHandler(v -> {
        Assert.assertEquals(0, req.formAttributes().size());
        req.response().putHeader("content-type", "text/plain").end("done");
      });
    });
    startServer(ctx);

    String contentType = "multipart/form-data; boundary=a4e41223-a527-49b6-ac1c-315d76be757e";
    String contentLength = "225";
    String body = "--a4e41223-a527-49b6-ac1c-315d76be757e\r\n" +
        "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
        "Content-Type: image/gif; charset=utf-8\r\n" +
        "Content-Length: 12\r\n" +
        "\r\n" +
        "some-content\r\n" +
        "--a4e41223-a527-49b6-ac1c-315d76be757e--\r\n";

    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, POST("/form").
          set("content-type", contentType).set("content-length", contentLength), 0, false, request.context.newPromise());
      request.encoder.writeData(request.context, id, BufferInternal.buffer(body).getByteBuf(), 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testConnect() throws Exception {
    server.requestHandler(req -> {
      Assert.assertEquals(HttpMethod.CONNECT, req.method());
      Assert.assertEquals("whatever.com", req.authority().host());
      Assert.assertNull(req.path());
      Assert.assertNull(req.query());
      Assert.assertNull(req.scheme());
      Assert.assertNull(req.uri());
      Assert.assertNull(req.absoluteURI());
      testComplete();
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2Headers headers = new DefaultHttp2Headers().method("CONNECT").authority("whatever.com");
      request.encoder.writeHeaders(request.context, id, headers, 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testServerRequestPauseResume() throws Exception {
    testStreamPauseResume(req -> Future.succeededFuture(req));
  }

  private void testStreamPauseResume(Function<HttpServerRequest, Future<ReadStream<Buffer>>> streamProvider) throws Exception {
    Buffer expected = Buffer.buffer();
    String chunk = TestUtils.randomAlphaString(1000);
    AtomicBoolean done = new AtomicBoolean();
    AtomicBoolean paused = new AtomicBoolean();
    Buffer received = Buffer.buffer();
    server.requestHandler(req -> {
      Future<ReadStream<Buffer>> fut = streamProvider.apply(req);
      fut.onComplete(TestUtils.onSuccess(stream -> {
        vertx.setPeriodic(1, timerID -> {
          if (paused.get()) {
            vertx.cancelTimer(timerID);
            done.set(true);
            // Let some time to accumulate some more buffers
            vertx.setTimer(100, id -> {
              stream.resume();
            });
          }
        });
        stream.handler(received::appendBuffer);
        stream.endHandler(v -> {
          Assert.assertEquals(expected, received);
          testComplete();
        });
        stream.pause();
      }));
    });
    startServer();

    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, POST("/form").
          set("content-type", "text/plain"), 0, false, request.context.newPromise());
      request.context.flush();
      Http2Stream stream = request.connection.stream(id);
      class Anonymous {
        void send() {
          boolean writable = request.encoder.flowController().isWritable(stream);
          if (writable) {
            Buffer buf = Buffer.buffer(chunk);
            expected.appendBuffer(buf);
            request.encoder.writeData(request.context, id, ((BufferInternal)buf).getByteBuf(), 0, false, request.context.newPromise());
            request.context.flush();
            request.context.executor().execute(this::send);
          } else {
            request.encoder.writeData(request.context, id, Unpooled.EMPTY_BUFFER, 0, true, request.context.newPromise());
            request.context.flush();
            paused.set(true);
          }
        }
      }
      new Anonymous().send();
    });
    await();
  }

  @Test
  public void testServerResponseWritability() throws Exception {
    testStreamWritability(req -> {
      HttpServerResponse resp = req.response();
      resp.putHeader("content-type", "text/plain");
      resp.setChunked(true);
      return Future.succeededFuture(resp);
    });
  }

  private void testStreamWritability(Function<HttpServerRequest, Future<WriteStream<Buffer>>> streamProvider) throws Exception {
    Context ctx = vertx.getOrCreateContext();
    String content = TestUtils.randomAlphaString(1024);
    StringBuilder expected = new StringBuilder();
    Promise<Void> whenFull = Promise.promise();
    AtomicBoolean drain = new AtomicBoolean();
    server.requestHandler(req -> {
      Future<WriteStream<Buffer>> fut = streamProvider.apply(req);
      fut.onComplete(TestUtils.onSuccess(stream -> {
        vertx.setPeriodic(1, timerID -> {
          if (stream.writeQueueFull()) {
            stream.drainHandler(v -> {
              TestUtils.assertOnIOContext(ctx);
              expected.append("last");
              stream.end(Buffer.buffer("last"));
            });
            vertx.cancelTimer(timerID);
            drain.set(true);
            whenFull.complete();
          } else {
            expected.append(content);
            Buffer buf = Buffer.buffer(content);
            stream.write(buf);
          }
        });
      }));
    });
    startServer(ctx);

    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      AtomicInteger toAck = new AtomicInteger();
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.decoder.frameListener(new Http2FrameAdapter() {

        StringBuilder received = new StringBuilder();

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          received.append(data.toString(StandardCharsets.UTF_8));
          int delta = super.onDataRead(ctx, streamId, data, padding, endOfStream);
          if (endOfStream) {
            vertx.runOnContext(v -> {
              Assert.assertEquals(expected.toString(), received.toString());
              testComplete();
            });
            return delta;
          } else {
            if (drain.get()) {
              return delta;
            } else {
              toAck.getAndAdd(delta);
              return 0;
            }
          }
        }
      });
      whenFull.future().onComplete(ar -> {
        request.context.executor().execute(() -> {
          try {
            request.decoder.flowController().consumeBytes(request.connection.stream(id), toAck.intValue());
            request.context.flush();
          } catch (Http2Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
          }
        });
      });
    });


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
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        int count;
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          switch (count++) {
            case 0:
              vertx.runOnContext(v -> {
                Assert.assertFalse(endStream);
              });
              break;
            case 1:
              vertx.runOnContext(v -> {
                Assert.assertEquals("foo_value", headers.get("foo").toString());
                Assert.assertEquals(1, headers.getAll("foo").size());
                Assert.assertEquals("foo_value", headers.getAll("foo").get(0).toString());
                Assert.assertEquals("bar_value", headers.getAll("bar").get(0).toString());
                Assert.assertEquals(2, headers.getAll("juu").size());
                Assert.assertEquals("juu_value_1", headers.getAll("juu").get(0).toString());
                Assert.assertEquals("juu_value_2", headers.getAll("juu").get(1).toString());
                Assert.assertTrue(endStream);
                testComplete();
              });
              break;
            default:
              vertx.runOnContext(v -> {
                Assert.fail();
              });
              break;
          }
        }
      });
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testServerResetClientStream1() throws Exception {
    server.requestHandler(req -> {
      req.handler(buf -> {
        req.response().reset(8);
      });
    });
    testServerResetClientStream(code -> {
      Assert.assertEquals(8, code);
      testComplete();
    }, false);
  }

  @Test
  public void testServerResetClientStream2() throws Exception {
    server.requestHandler(req -> {
      req.handler(buf -> {
        req.response().end();
        req.response().reset(8);
      });
    });
    testServerResetClientStream(code -> {
      Assert.assertEquals(8, code);
      testComplete();
    }, false);
  }

  @Test
  public void testServerResetClientStream3() throws Exception {
    server.requestHandler(req -> {
      req.endHandler(buf -> {
        req.response().reset(8);
      });
    });
    testServerResetClientStream(code -> {
      Assert.assertEquals(8, code);
      testComplete();
    }, true);
  }

  private void testServerResetClientStream(LongConsumer resetHandler, boolean end) throws Exception {
    startServer();

    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
          vertx.runOnContext(v -> {
            resetHandler.accept(errorCode);
          });
        }
      });
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      encoder.writeData(request.context, id, BufferInternal.buffer("hello").getByteBuf(), 0, end, request.context.newPromise());
    });


    await();
  }

  @Test
  public void testClientResetServerStream() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    Promise<Void> bufReceived = Promise.promise();
    AtomicInteger resetCount = new AtomicInteger();
    server.requestHandler(req -> {
      req.handler(buf -> {
        bufReceived.complete();
      });
      req.exceptionHandler(err -> {
        TestUtils.assertOnIOContext(ctx);
        if (err instanceof StreamResetException) {
          Assert.assertEquals(10L, ((StreamResetException) err).getCode());
          Assert.assertEquals(0, resetCount.getAndIncrement());
        }
      });
      req.response().exceptionHandler(err -> {
        TestUtils.assertOnIOContext(ctx);
        if (err instanceof StreamResetException) {
          Assert.assertEquals(10L, ((StreamResetException) err).getCode());
          Assert.assertEquals(1, resetCount.getAndIncrement());
          testComplete();
        }
      });
    });
    startServer(ctx);

    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      encoder.writeData(request.context, id, BufferInternal.buffer("hello").getByteBuf(), 0, false, request.context.newPromise());
      bufReceived.future().onComplete(ar -> {
        encoder.writeRstStream(request.context, id, 10, request.context.newPromise());
        request.context.flush();
      });
    });


    await();
  }

  @Test
  public void testConnectionClose() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      HttpConnection conn = req.connection();
      conn.closeHandler(v -> {
        Assert.assertSame(ctx, Vertx.currentContext());
        testComplete();
      });
      req.response().putHeader("Content-Type", "text/plain").end();
    });
    startServer(ctx);

    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.decoder.frameListener(new Http2FrameAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          request.context.close();
        }
      });
    });
    await();
  }

  @Test
  public void testPushPromise() throws Exception {
    testPushPromise(GET("/").authority("whatever.com"), (resp, handler ) -> {
      resp.push(HttpMethod.GET, "/wibble").onComplete(handler);
    }, headers -> {
      Assert.assertEquals("GET", headers.method().toString());
      Assert.assertEquals("https", headers.scheme().toString());
      Assert.assertEquals("/wibble", headers.path().toString());
      Assert.assertEquals("whatever.com", headers.authority().toString());
    });
  }

  @Test
  public void testPushPromiseHeaders() throws Exception {
    testPushPromise(GET("/").authority("whatever.com"), (resp, handler ) -> {
      resp.push(HttpMethod.GET, "/wibble", HttpHeaders.
          set("foo", "foo_value").
          set("bar", Arrays.<CharSequence>asList("bar_value_1", "bar_value_2"))).onComplete(handler);
    }, headers -> {
      Assert.assertEquals("GET", headers.method().toString());
      Assert.assertEquals("https", headers.scheme().toString());
      Assert.assertEquals("/wibble", headers.path().toString());
      Assert.assertEquals("whatever.com", headers.authority().toString());
      Assert.assertEquals("foo_value", headers.get("foo").toString());
      Assert.assertEquals(Arrays.asList("bar_value_1", "bar_value_2"), headers.getAll("bar").stream().map(CharSequence::toString).collect(Collectors.toList()));
    });
  }

  @Test
  public void testPushPromiseNoAuthority() throws Exception {
    Http2Headers get = GET("/");
    get.remove("authority");
    testPushPromise(get, (resp, handler ) -> {
      resp.push(HttpMethod.GET, "/wibble").onComplete(handler);
    }, headers -> {
      Assert.assertEquals("GET", headers.method().toString());
      Assert.assertEquals("https", headers.scheme().toString());
      Assert.assertEquals("/wibble", headers.path().toString());
      Assert.assertNull(headers.authority());
    });
  }

  @Test
  public void testPushPromiseOverrideAuthority() throws Exception {
    testPushPromise(GET("/").authority("whatever.com"), (resp, handler ) -> {
      resp.push(HttpMethod.GET, HostAndPort.authority("override.com"), "/wibble").onComplete(handler);
    }, headers -> {
      Assert.assertEquals("GET", headers.method().toString());
      Assert.assertEquals("https", headers.scheme().toString());
      Assert.assertEquals("/wibble", headers.path().toString());
      Assert.assertEquals("override.com", headers.authority().toString());
    });
  }


  private void testPushPromise(Http2Headers requestHeaders,
                               BiConsumer<HttpServerResponse, Handler<AsyncResult<HttpServerResponse>>> pusher,
                               Consumer<Http2Headers> headerChecker) throws Exception {
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      Handler<AsyncResult<HttpServerResponse>> handler = ar -> {
        TestUtils.assertSameEventLoop(ctx, Vertx.currentContext());
        Assert.assertTrue(ar.succeeded());
        HttpServerResponse response = ar.result();
        response./*putHeader("content-type", "application/plain").*/end("the_content");
        assertIllegalStateException(() -> response.push(HttpMethod.GET, "/wibble2"));
      };
      pusher.accept(req.response(), handler);
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, requestHeaders, 0, true, request.context.newPromise());
      Map<Integer, Http2Headers> pushed = new HashMap<>();
      request.decoder.frameListener(new Http2FrameAdapter() {
        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
          pushed.put(promisedStreamId, headers);
        }

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          int delta = super.onDataRead(ctx, streamId, data, padding, endOfStream);
          String content = data.toString(StandardCharsets.UTF_8);
          vertx.runOnContext(v -> {
            Assert.assertEquals(Collections.singleton(streamId), pushed.keySet());
            Assert.assertEquals("the_content", content);
            Http2Headers pushedHeaders = pushed.get(streamId);
            headerChecker.accept(pushedHeaders);
            testComplete();
          });
          return delta;
        }
      });
    });
    await();
  }

  @Test
  public void testResetActivePushPromise() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/wibble").onComplete(TestUtils.onSuccess(response -> {
        TestUtils.assertOnIOContext(ctx);
        AtomicInteger resets = new AtomicInteger();
        response.exceptionHandler(err -> {
          if (err instanceof StreamResetException) {
            Assert.assertEquals(8, ((StreamResetException)err).getCode());
            resets.incrementAndGet();
          }
        });
        response.closeHandler(v -> {
          testComplete();
          Assert.assertEquals(1, resets.get());
        });
        response.setChunked(true).write("some_content");
      }));
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.decoder.frameListener(new Http2FrameAdapter() {
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          request.encoder.writeRstStream(ctx, streamId, Http2Error.CANCEL.code(), ctx.newPromise());
          request.context.flush();
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
    });
    await();
  }

  @Test
  public void testQueuePushPromise() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    int numPushes = 10;
    Set<String> pushSent = new HashSet<>();
    server.requestHandler(req -> {
      req.response().setChunked(true).write("abc");
      for (int i = 0; i < numPushes; i++) {
        int val = i;
        String path = "/wibble" + val;
        req.response().push(HttpMethod.GET, path).onComplete(TestUtils.onSuccess(response -> {
          TestUtils.assertSameEventLoop(ctx, Vertx.currentContext());
          pushSent.add(path);
          vertx.setTimer(10, id -> {
            response.end("wibble-" + val);
          });
        }));
      }
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.settings.maxConcurrentStreams(3);
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.decoder.frameListener(new Http2FrameAdapter() {
        int count = numPushes;
        Set<String> pushReceived = new HashSet<>();

        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
          pushReceived.add(headers.path().toString());
        }

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          if (count-- == 0) {
            vertx.runOnContext(v -> {
              Assert.assertEquals(numPushes, pushSent.size());
              Assert.assertEquals(pushReceived, pushSent);
              testComplete();
            });
          }
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
    });
    await();
  }

  @Test
  public void testResetPendingPushPromise() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/wibble").onComplete(TestUtils.onFailure(r -> {
        TestUtils.assertOnIOContext(ctx);
        testComplete();
      }));
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.settings.maxConcurrentStreams(0);
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.decoder.frameListener(new Http2FrameAdapter() {
        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
          request.encoder.writeRstStream(request.context, promisedStreamId, Http2Error.CANCEL.code(), request.context.newPromise());
          request.context.flush();
        }
      });
    });
    await();
  }

  @Test
  public void testHostHeaderInsteadOfAuthorityPseudoHeader() throws Exception {
    // build the HTTP/2 headers, omit the ":authority" pseudo-header and include the "host" header instead
    Http2Headers headers = new DefaultHttp2Headers().method("GET").scheme("https").path("/").set("host", DEFAULT_HTTPS_HOST_AND_PORT);
    server.requestHandler(req -> {
      // validate that the authority is properly populated
      Assert.assertEquals(DEFAULT_HTTPS_HOST, req.authority().host());
      Assert.assertEquals(DEFAULT_HTTPS_PORT, req.authority().port());
      testComplete();
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, headers, 0, true, request.context.newPromise());
    });
    await();
  }

  @Test
  public void testMissingMethodPseudoHeader() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().scheme("http").path("/"));
  }

  @Test
  public void testMissingSchemePseudoHeader() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("GET").path("/"));
  }

  @Test
  public void testMissingPathPseudoHeader() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("GET").scheme("http"));
  }

  @Test
  public void testInvalidAuthority() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("GET").scheme("http").authority("foo@" + DEFAULT_HTTPS_HOST_AND_PORT).path("/"));
  }

  @Test
  public void testInvalidHost1() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("GET").scheme("http").authority(DEFAULT_HTTPS_HOST_AND_PORT).path("/").set("host", "foo@" + DEFAULT_HTTPS_HOST_AND_PORT));
  }

  @Test
  public void testInvalidHost2() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("GET").scheme("http").authority(DEFAULT_HTTPS_HOST_AND_PORT).path("/").set("host", "another-host:" + DEFAULT_HTTPS_PORT));
  }

  @Test
  public void testInvalidHost3() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("GET").scheme("http").authority(DEFAULT_HTTPS_HOST_AND_PORT).path("/").set("host", DEFAULT_HTTP_HOST));
  }

  @Test
  public void testConnectInvalidPath() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("CONNECT").path("/").authority(DEFAULT_HTTPS_HOST_AND_PORT));
  }

  @Test
  public void testConnectInvalidScheme() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("CONNECT").scheme("http").authority(DEFAULT_HTTPS_HOST_AND_PORT));
  }

  @Test
  public void testConnectInvalidAuthority() throws Exception {
    testMalformedRequestHeaders(new DefaultHttp2Headers().method("CONNECT").authority("foo@" + DEFAULT_HTTPS_HOST_AND_PORT));
  }

  private void testMalformedRequestHeaders(Http2Headers headers) throws Exception {
    server.requestHandler(req -> Assert.fail());
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, headers, 0, true, request.context.newPromise());
      request.decoder.frameListener(new Http2FrameAdapter() {
        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
          vertx.runOnContext(v -> {
            testComplete();
          });
        }
      });
    });
    await();
  }

  @Test
  public void testRequestHandlerFailure() throws Exception {
    testHandlerFailure(false, (err, server) -> {
      server.requestHandler(req -> {
        throw err;
      });
    });
  }

  @Test
  public void testRequestEndHandlerFailure() throws Exception {
    testHandlerFailure(false, (err, server) -> {
      server.requestHandler(req -> {
        req.endHandler(v -> {
          throw err;
        });
      });
    });
  }

  @Test
  public void testRequestEndHandlerFailureWithData() throws Exception {
    testHandlerFailure(true, (err, server) -> {
      server.requestHandler(req -> {
        req.endHandler(v -> {
          throw err;
        });
      });
    });
  }

  @Test
  public void testRequestDataHandlerFailure() throws Exception {
    testHandlerFailure(true, (err, server) -> {
      server.requestHandler(req -> {
        req.handler(buf -> {
          throw err;
        });
      });
    });
  }

  private void testHandlerFailure(boolean data, BiConsumer<RuntimeException, HttpServer> configurator) throws Exception {
    RuntimeException failure = new RuntimeException();
    io.vertx.core.http.Http2Settings settings = TestUtils.randomHttp2Settings();
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions(serverOptions).setInitialSettings(settings));
    configurator.accept(failure, server);
    Context ctx = vertx.getOrCreateContext();
    ctx.exceptionHandler(err -> {
      Assert.assertSame(err, failure);
      testComplete();
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, !data, request.context.newPromise());
      if (data) {
        request.encoder.writeData(request.context, id, BufferInternal.buffer("hello").getByteBuf(), 0, true, request.context.newPromise());
      }
    });
    await();
  }

  private static File createTempFile(Buffer buffer) throws Exception {
    File f = File.createTempFile("vertx", ".bin");
    f.deleteOnExit();
    try(FileOutputStream out = new FileOutputStream(f)) {
      out.write(buffer.getBytes());
    }
    return f;
  }

  @Test
  public void testSendFile() throws Exception {
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(1000 * 1000));
    File tmp = createTempFile(expected);
    testSendFile(expected, tmp.getAbsolutePath(), 0, expected.length());
  }

  @Test
  public void testSendFileRange() throws Exception {
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(1000 * 1000));
    File tmp = createTempFile(expected);
    int from = 200 * 1000;
    int to = 700 * 1000;
    testSendFile(expected.slice(from, to), tmp.getAbsolutePath(), from, to - from);
  }

  @Test
  public void testSendEmptyFile() throws Exception {
    Buffer expected = Buffer.buffer();
    File tmp = createTempFile(expected);
    testSendFile(expected, tmp.getAbsolutePath(), 0, expected.length());
  }

  private void testSendFile(Buffer expected, String path, long offset, long length) throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.sendFile(path, offset, length).onComplete(TestUtils.onSuccess(v -> {
        Assert.assertEquals(resp.bytesWritten(), length);
        complete();
      }));
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        Buffer buffer = Buffer.buffer();
        Http2Headers responseHeaders;
        private void endStream() {
          vertx.runOnContext(v -> {
            Assert.assertEquals("" + length, responseHeaders.get("content-length").toString());
            Assert.assertEquals(expected, buffer);
            complete();
          });
        }
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          responseHeaders = headers;
          if (endStream) {
            endStream();
          }
        }
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          buffer.appendBuffer(BufferInternal.buffer(data.duplicate()));
          if (endOfStream) {
            endStream();
          }
          return data.readableBytes() + padding;
        }
      });
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testStreamError() throws Exception {
    Promise<Void> when = Promise.promise();
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      AtomicInteger reqErrors = new AtomicInteger();
      req.exceptionHandler(err -> {
        // Called twice : reset + close
        TestUtils.assertOnIOContext(ctx);
        reqErrors.incrementAndGet();
      });
      AtomicInteger respErrors = new AtomicInteger();
      req.response().exceptionHandler(err -> {
        TestUtils.assertOnIOContext(ctx);
        respErrors.incrementAndGet();
      });
      req.response().closeHandler(v -> {
        TestUtils.assertOnIOContext(ctx);
        Assert.assertTrue("Was expecting reqErrors to be > 0", reqErrors.get() > 0);
        Assert.assertTrue("Was expecting respErrors to be > 0", respErrors.get() > 0);
        testComplete();
      });
      req.response().endHandler(v -> {
        Assert.fail();
      });
      when.complete();
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      request.context.flush();
      when.future().onComplete(ar -> {
        // Send a corrupted frame on purpose to check we get the corresponding error in the request exception handler
        // the error is : greater padding value 0c -> 1F
        // ChannelFuture a = encoder.frameWriter().writeData(request.context, id, Buffer.buffer("hello").getByteBuf(), 12, false, request.context.newPromise());
        // normal frame    : 00 00 12 00 08 00 00 00 03 0c 68 65 6c 6c 6f 00 00 00 00 00 00 00 00 00 00 00 00
        // corrupted frame : 00 00 12 00 08 00 00 00 03 1F 68 65 6c 6c 6f 00 00 00 00 00 00 00 00 00 00 00 00
        request.channel.write(BufferInternal.buffer(new byte[]{
            0x00, 0x00, 0x12, 0x00, 0x08, 0x00, 0x00, 0x00, (byte)(id & 0xFF), 0x1F, 0x68, 0x65, 0x6c, 0x6c,
            0x6f, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        }).getByteBuf());
        request.context.flush();
      });
    });
    await();
  }

  @Test
  public void testPromiseStreamError() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    Promise<Void> when = Promise.promise();
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/wibble").onComplete(TestUtils.onSuccess(resp -> {
        TestUtils.assertOnIOContext(ctx);
        when.complete();
        AtomicInteger erros = new AtomicInteger();
        resp.exceptionHandler(err -> {
          TestUtils.assertOnIOContext(ctx);
          erros.incrementAndGet();
        });
        resp.closeHandler(v -> {
          TestUtils.assertOnIOContext(ctx);
          Assert.assertTrue("Was expecting errors to be > 0", erros.get() > 0);
          testComplete();
        });
        resp.endHandler(v -> {
          Assert.fail();
        });
        resp.setChunked(true).write("whatever"); // Transition to half-closed remote
      }));
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
          when.future().onComplete(ar -> {
            Http2ConnectionEncoder encoder = request.encoder;
            encoder.frameWriter().writeHeaders(request.context, promisedStreamId, GET("/"), 0, false, request.context.newPromise());
            request.context.flush();
          });
        }
      });
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testConnectionDecodeError() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    waitFor(2);
    Promise<Void> when = Promise.promise();
    server.requestHandler(req -> {
      AtomicInteger reqFailures = new AtomicInteger();
      AtomicInteger respFailures = new AtomicInteger();
      req.exceptionHandler(err -> {
        TestUtils.assertOnIOContext(ctx);
        reqFailures.incrementAndGet();
      });
      req.response().exceptionHandler(err -> {
        TestUtils.assertOnIOContext(ctx);
        respFailures.incrementAndGet();
      });
      req.response().closeHandler(v -> {
        TestUtils.assertOnIOContext(ctx);
        complete();
      });
      req.response().endHandler(v -> {
        Assert.fail();
      });
      HttpConnection conn = req.connection();
      AtomicInteger connFailures = new AtomicInteger();
      conn.exceptionHandler(err -> {
        TestUtils.assertOnIOContext(ctx);
        connFailures.incrementAndGet();
      });
      conn.closeHandler(v -> {
        Assert.assertTrue(connFailures.get() > 0);
        TestUtils.assertOnIOContext(ctx);
        complete();
      });
      when.complete();
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      when.future().onComplete(ar -> {
        // Send a stream ID that does not exists
        encoder.frameWriter().writeRstStream(request.context, 10, 0, request.context.newPromise());
        request.context.flush();
      });
      encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testServerSendGoAwayNoError() throws Exception {
    waitFor(2);
    AtomicReference<HttpServerRequest> first = new AtomicReference<>();
    AtomicInteger status = new AtomicInteger();
    AtomicInteger closed = new AtomicInteger();
    AtomicBoolean done = new AtomicBoolean();
    Context ctx = vertx.getOrCreateContext();
    Handler<HttpServerRequest> requestHandler = req -> {
      if (first.compareAndSet(null, req)) {
        req.exceptionHandler(err -> {
          Assert.assertTrue(done.get());
        });
        req.response().exceptionHandler(err -> {
          Assert.assertTrue(done.get());
        });
      } else {
        Assert.assertEquals(0, status.getAndIncrement());
        req.exceptionHandler(err -> {
          closed.incrementAndGet();
        });
        req.response().exceptionHandler(err -> {
          Assert.assertEquals(HttpClosedException.class, err.getClass());
          Assert.assertEquals(0, ((HttpClosedException)err).goAway().getErrorCode());
          closed.incrementAndGet();
        });
        HttpConnection conn = req.connection();
        conn.shutdownHandler(v -> {
          Assert.assertFalse(done.get());
        });
        conn.closeHandler(v -> {
          Assert.assertTrue(done.get());
        });
        ctx.runOnContext(v1 -> {
          conn.goAway(0, (int)first.get().response().streamId());
          vertx.setTimer(300, timerID -> {
            Assert.assertEquals(1, status.getAndIncrement());
            done.set(true);
            complete();
          });
        });
      }
    };
    testServerSendGoAway(requestHandler, 0);
  }

  @Ignore
  @Test
  public void testServerSendGoAwayInternalError() throws Exception {
    waitFor(3);
    AtomicReference<HttpServerRequest> first = new AtomicReference<>();
    AtomicInteger status = new AtomicInteger();
    AtomicInteger closed = new AtomicInteger();
    Handler<HttpServerRequest> requestHandler = req -> {
      if (first.compareAndSet(null, req)) {
        req.exceptionHandler(err -> {
          Assert.fail();
        });
        req.response().closeHandler(err -> {
          closed.incrementAndGet();
        });
        req.response().endHandler(err -> {
          closed.incrementAndGet();
        });
      } else {
        Assert.assertEquals(0, status.getAndIncrement());
        req.exceptionHandler(err -> {
          closed.incrementAndGet();
        });
        req.response().closeHandler(err -> {
          closed.incrementAndGet();
        });
        req.response().endHandler(err -> {
          closed.incrementAndGet();
        });
        HttpConnection conn = req.connection();
        conn.closeHandler(v -> {
          Assert.assertEquals(5, closed.get());
          Assert.assertEquals(1, status.get());
          complete();
        });
        conn.shutdownHandler(v -> {
          Assert.assertEquals(1, status.get());
          complete();
        });
        conn.goAway(2, (int)first.get().response().streamId());
      }
    };
    testServerSendGoAway(requestHandler, 2);
  }

  @Test
  public void testShutdownWithTimeout() throws Exception {
    waitFor(4);
    AtomicReference<HttpServerRequest> first = new AtomicReference<>();
    AtomicInteger status = new AtomicInteger();
    Handler<HttpServerRequest> requestHandler = req -> {
      if (first.compareAndSet(null, req)) {
        req.exceptionHandler(err -> {
          Assert.fail();
        });
        req.response().closeHandler(err -> {
          complete();
        });
        req.response().endHandler(err -> {
          Assert.fail();
        });
      } else {
        Assert.assertEquals(0, status.getAndIncrement());
        req.exceptionHandler(err -> {
          Assert.fail();
        });
        req.response().closeHandler(err -> {
          complete();
        });
        req.response().endHandler(err -> {
          Assert.fail();
        });
        HttpConnection conn = req.connection();
        conn.closeHandler(v -> {
          Assert.assertEquals(1, status.getAndIncrement());
          complete();
        });
        conn.shutdown(300, TimeUnit.MILLISECONDS);
      }
    };
    testServerSendGoAway(requestHandler, 0);
  }

  @Test
  public void testShutdown() throws Exception {
    waitFor(2);
    AtomicReference<HttpServerRequest> first = new AtomicReference<>();
    AtomicInteger status = new AtomicInteger();
    Handler<HttpServerRequest> requestHandler = req -> {
      if (first.compareAndSet(null, req)) {
        req.exceptionHandler(err -> {
          Assert.fail();
        });
        req.response().exceptionHandler(err -> {
          Assert.fail();
        });
      } else {
        Assert.assertEquals(0, status.getAndIncrement());
        req.exceptionHandler(err -> {
          Assert.fail();
        });
        req.response().exceptionHandler(err -> {
          Assert.fail();
        });
        HttpConnection conn = req.connection();
        conn.closeHandler(v -> {
          Assert.assertEquals(2, status.getAndIncrement());
          complete();
        });
        conn.shutdown();
        vertx.setTimer(300, timerID -> {
          Assert.assertEquals(1, status.getAndIncrement());
          first.get().response().end();
          req.response().end();
        });
      }
    };
    testServerSendGoAway(requestHandler, 0);
  }

  private void testServerSendGoAway(Handler<HttpServerRequest> requestHandler, int expectedError) throws Exception {
    server.requestHandler(requestHandler);
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {
          vertx.runOnContext(v -> {
            Assert.assertEquals(expectedError, errorCode);
            complete();
          });
        }
      });
      Http2ConnectionEncoder encoder = request.encoder;
      int id1 = request.nextStreamId();
      encoder.writeHeaders(request.context, id1, GET("/"), 0, true, request.context.newPromise());
      int id2 = request.nextStreamId();
      encoder.writeHeaders(request.context, id2, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();

    });
    await();
  }

  @Test
  public void testServerClose() throws Exception {
    waitFor(2);
    AtomicInteger status = new AtomicInteger();
    Handler<HttpServerRequest> requestHandler = req -> {
      HttpConnection conn = req.connection();
      conn.shutdownHandler(v -> {
        Assert.assertEquals(0, status.getAndIncrement());
      });
      conn.closeHandler(v -> {
        Assert.assertEquals(1, status.getAndIncrement());
        complete();
      });
      conn.close();
    };
    server.requestHandler(requestHandler);
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.channel.closeFuture().addListener(v1 -> {
        vertx.runOnContext(v2 -> {
          complete();
        });
      });
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {
          vertx.runOnContext(v -> {
            Assert.assertEquals(0, errorCode);
          });
        }
      });
      Http2ConnectionEncoder encoder = request.encoder;
      int id = request.nextStreamId();
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();

    });
    await();
  }

  @Test
  public void testClientSendGoAwayNoError() throws Exception {
    Promise<Void> abc = Promise.promise();
    Context ctx = vertx.getOrCreateContext();
    Handler<HttpServerRequest> requestHandler = req -> {
      HttpConnection conn = req.connection();
      AtomicInteger numShutdown = new AtomicInteger();
      AtomicBoolean completed = new AtomicBoolean();
      conn.shutdownHandler(v -> {
        TestUtils.assertOnIOContext(ctx);
        numShutdown.getAndIncrement();
        vertx.setTimer(100, timerID -> {
          // Delay so we can check the connection is not closed
          completed.set(true);
          testComplete();
        });
      });
      conn.goAwayHandler(ga -> {
        TestUtils.assertOnIOContext(ctx);
        Assert.assertEquals(0, numShutdown.get());
        req.response().end();
      });
      conn.closeHandler(v -> {
        Assert.assertTrue(completed.get());
      });
      abc.complete();
    };
    server.requestHandler(requestHandler);
    startServer(ctx);
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      Http2ConnectionEncoder encoder = request.encoder;
      int id = request.nextStreamId();
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
      abc.future().onComplete(ar -> {
        encoder.writeGoAway(request.context, id, 0, Unpooled.EMPTY_BUFFER, request.context.newPromise());
        request.context.flush();
      });
    });
    await();
  }

  @Test
  public void testClientSendGoAwayInternalError() throws Exception {
    waitFor(3);
    // On windows the client will close the channel immediately (since it's an error)
    // and the server might see the channel inactive without receiving the close frame before
    Assume.assumeFalse(Utils.isWindows());
    Promise<Void> continuation = Promise.promise();
    Context ctx = vertx.getOrCreateContext();
    Handler<HttpServerRequest> requestHandler = req -> {
      HttpConnection conn = req.connection();
      AtomicInteger status = new AtomicInteger();
      conn.goAwayHandler(ga -> {
        TestUtils.assertOnIOContext(ctx);
        Assert.assertEquals(0, status.getAndIncrement());
        req.response().end();
        complete();
      });
      conn.shutdownHandler(v -> {
        TestUtils.assertOnIOContext(ctx);
        Assert.assertEquals(1, status.getAndIncrement());
        complete();
      });
      conn.closeHandler(v -> {
        Assert.assertEquals(2, status.getAndIncrement());
         complete();
      });
      continuation.complete();
    };
    server.requestHandler(requestHandler);
    startServer(ctx);
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      Http2ConnectionEncoder encoder = request.encoder;
      int id = request.nextStreamId();
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
      continuation.future().onComplete(ar -> {
        encoder.writeGoAway(request.context, id, 3, Unpooled.EMPTY_BUFFER, request.context.newPromise());
        request.context.flush();
      });
    });
    await();
  }

  @Test
  public void testShutdownOverride() throws Exception {
    AtomicLong shutdown = new AtomicLong();
    Handler<HttpServerRequest> requestHandler = req -> {
      HttpConnection conn = req.connection();
      shutdown.set(System.currentTimeMillis());
      conn.shutdown(10, TimeUnit.SECONDS);
      vertx.setTimer(300, v -> {
        conn.shutdown(300, TimeUnit.MILLISECONDS);
      });
    };
    server.requestHandler(requestHandler);
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.channel.closeFuture().addListener(v1 -> {
        vertx.runOnContext(v2 -> {
          Assert.assertTrue(shutdown.get() - System.currentTimeMillis() < 1200);
          testComplete();
        });
      });
      Http2ConnectionEncoder encoder = request.encoder;
      int id = request.nextStreamId();
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testRequestResponseLifecycle() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.endHandler(v -> {
        assertIllegalStateException(() -> req.setExpectMultipart(false));
        assertIllegalStateException(() -> req.handler(buf -> {}));
        assertIllegalStateException(() -> req.uploadHandler(upload -> {}));
        assertIllegalStateException(() -> req.endHandler(v2 -> {}));
        complete();
      });
      HttpServerResponse resp = req.response();
      resp.setChunked(true).write(Buffer.buffer("whatever"));
      Assert.assertTrue(resp.headWritten());
      assertIllegalStateException(() -> resp.setChunked(false));
      assertIllegalStateException(() -> resp.setStatusCode(100));
      assertIllegalStateException(() -> resp.setStatusMessage("whatever"));
      assertIllegalStateException(() -> resp.putHeader("a", "b"));
      assertIllegalStateException(() -> resp.putHeader("a", (CharSequence) "b"));
      assertIllegalStateException(() -> resp.putHeader("a", (Iterable<String>)Arrays.asList("a", "b")));
      assertIllegalStateException(() -> resp.putHeader("a", (Arrays.<CharSequence>asList("a", "b"))));
      assertIllegalStateException(resp::writeContinue);
      resp.end();
      assertIllegalStateException(() -> resp.write("a"));
      assertIllegalStateException(() -> resp.write("a", "UTF-8"));
      assertIllegalStateException(() -> resp.write(Buffer.buffer("a")));
      assertIllegalStateException(resp::end);
      assertIllegalStateException(() -> resp.end("a"));
      assertIllegalStateException(() -> resp.end("a", "UTF-8"));
      assertIllegalStateException(() -> resp.end(Buffer.buffer("a")));
      assertIllegalStateException(() -> resp.sendFile("the-file.txt"));
      assertIllegalStateException(() -> resp.closeHandler(v -> {}));
      assertIllegalStateException(() -> resp.endHandler(v -> {}));
      assertIllegalStateException(() -> resp.drainHandler(v -> {}));
      assertIllegalStateException(() -> resp.exceptionHandler(err -> {}));
      assertIllegalStateException(resp::writeQueueFull);
      assertIllegalStateException(() -> resp.setWriteQueueMaxSize(100));
      assertIllegalStateException(() -> resp.putTrailer("a", "b"));
      assertIllegalStateException(() -> resp.putTrailer("a", (CharSequence) "b"));
      assertIllegalStateException(() -> resp.putTrailer("a", (Iterable<String>)Arrays.asList("a", "b")));
      assertIllegalStateException(() -> resp.putTrailer("a", (Arrays.<CharSequence>asList("a", "b"))));
      assertIllegalStateException(() -> resp.push(HttpMethod.GET, "/whatever"));
      complete();
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testResponseCompressionDisabled() throws Exception {
    waitFor(2);
    String expected = TestUtils.randomAlphaString(1000);
    server.requestHandler(req -> {
      req.response().end(expected);
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            Assert.assertEquals(null, headers.get(HttpHeaderNames.CONTENT_ENCODING));
            complete();
          });
        }
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          String s = data.toString(StandardCharsets.UTF_8);
          vertx.runOnContext(v -> {
            Assert.assertEquals(expected, s);
            complete();
          });
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/").add("accept-encoding", "gzip"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testResponseCompressionEnabled() throws Exception {
    waitFor(2);
    String expected = TestUtils.randomAlphaString(1000);
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions(serverOptions).setCompressionSupported(true));
    server.requestHandler(req -> {
      req.response().end(expected);
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            Assert.assertEquals("gzip", headers.get(HttpHeaderNames.CONTENT_ENCODING).toString());
            complete();
          });
        }
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          byte[] bytes = new byte[data.readableBytes()];
          data.readBytes(bytes);
          vertx.runOnContext(v -> {
            String decoded;
            try {
              GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes));
              ByteArrayOutputStream baos = new ByteArrayOutputStream();
              while (true) {
                int i = in.read();
                if (i == -1) {
                  break;
                }
                baos.write(i);
              }
              decoded = baos.toString();
            } catch (IOException e) {
              Assert.fail(e.getMessage());
              return;
            }
            Assert.assertEquals(expected, decoded);
            complete();
          });
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/").add("accept-encoding", "gzip"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testResponseCompressionEnabledButResponseAlreadyCompressed() throws Exception {
    waitFor(2);
    String expected = TestUtils.randomAlphaString(1000);
    server.close();
    server = vertx.createHttpServer(serverOptions.setCompressionSupported(true));
    server.requestHandler(req -> {
      req.response().headers().set(HttpHeaderNames.CONTENT_ENCODING, "gzip");
      try {
        req.response().end(Buffer.buffer(TestUtils.compressGzip(expected)));
      } catch (Exception e) {
        Assert.fail(e.getMessage());
      }
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            Assert.assertEquals("gzip", headers.get(HttpHeaderNames.CONTENT_ENCODING).toString());
            complete();
          });
        }
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          byte[] bytes = new byte[data.readableBytes()];
          data.readBytes(bytes);
          vertx.runOnContext(v -> {
            String decoded;
            try {
              GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes));
              ByteArrayOutputStream baos = new ByteArrayOutputStream();
              while (true) {
                int i = in.read();
                if (i == -1) {
                  break;
                }
                baos.write(i);
              }
              decoded = baos.toString();
            } catch (IOException e) {
              Assert.fail(e.getMessage());
              return;
            }
            Assert.assertEquals(expected, decoded);
            complete();
          });
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/").add("accept-encoding", "gzip"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testResponseCompressionEnabledButExplicitlyDisabled() throws Exception {
    waitFor(2);
    String expected = TestUtils.randomAlphaString(1000);
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions(serverOptions).setCompressionSupported(true));
    server.requestHandler(req -> {
      req.response().headers().set(HttpHeaderNames.CONTENT_ENCODING, "identity");
      try {
        req.response().end(expected);
      } catch (Exception e) {
        Assert.fail(e.getMessage());
      }
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            Assert.assertFalse(headers.contains(HttpHeaderNames.CONTENT_ENCODING));
            complete();
          });
        }
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          byte[] bytes = new byte[data.readableBytes()];
          data.readBytes(bytes);
          vertx.runOnContext(v -> {
            String decoded = new String(bytes, StandardCharsets.UTF_8);
            Assert.assertEquals(expected, decoded);
            complete();
          });
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/").add("accept-encoding", "gzip"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testResponseCompressionEnabledMixedStreams() throws Exception {
    waitFor(6);
    String expected = TestUtils.randomAlphaString(1000);
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions(serverOptions).setCompressionSupported(true));
    server.requestHandler(req -> {
      req.response().end(expected);
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      // Stream 1: with compression
      int id1 = request.nextStreamId();
      // Stream 2: without compression
      int id2 = request.nextStreamId();
      // Stream 3: with compression
      int id3 = request.nextStreamId();
      Map<Integer, ByteArrayOutputStream> streamData = new ConcurrentHashMap<>();
      streamData.put(id1, new ByteArrayOutputStream());
      streamData.put(id2, new ByteArrayOutputStream());
      streamData.put(id3, new ByteArrayOutputStream());
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            if (streamId == id1 || streamId == id3) {
              // Stream with accept-encoding: gzip should be compressed
              Assert.assertEquals("gzip", headers.get(HttpHeaderNames.CONTENT_ENCODING).toString());
            } else {
              // Stream without accept-encoding should not be compressed
              Assert.assertFalse(headers.contains(HttpHeaderNames.CONTENT_ENCODING));
            }
            complete();
          });
        }
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          byte[] bytes = new byte[data.readableBytes()];
          data.readBytes(bytes);
          ByteArrayOutputStream buf = streamData.get(streamId);
          buf.write(bytes, 0, bytes.length);
          if (endOfStream) {
            byte[] allBytes = buf.toByteArray();
            vertx.runOnContext(v -> {
              if (streamId == id1 || streamId == id3) {
                // Compressed stream - decode gzip
                String decoded;
                try {
                  GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(allBytes));
                  ByteArrayOutputStream baos = new ByteArrayOutputStream();
                  while (true) {
                    int i = in.read();
                    if (i == -1) {
                      break;
                    }
                    baos.write(i);
                  }
                  decoded = baos.toString();
                } catch (IOException e) {
                  fail(e);
                  return;
                }
                Assert.assertEquals(expected, decoded);
              } else {
                // Uncompressed stream - plain text
                Assert.assertEquals(expected, new String(allBytes, StandardCharsets.UTF_8));
              }
              complete();
            });
          }
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
      request.encoder.writeHeaders(request.context, id1, GET("/").add("accept-encoding", "gzip"), 0, true, request.context.newPromise());
      request.encoder.writeHeaders(request.context, id2, GET("/"), 0, true, request.context.newPromise());
      request.encoder.writeHeaders(request.context, id3, GET("/").add("accept-encoding", "gzip"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testRequestCompressionEnabled() throws Exception {
    String expected = TestUtils.randomAlphaString(1000);
    byte[] expectedGzipped = TestUtils.compressGzip(expected);
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions(serverOptions).setDecompressionSupported(true));
    server.requestHandler(req -> {
      StringBuilder postContent = new StringBuilder();
      req.handler(buff -> {
        postContent.append(buff.toString());
      });
      req.endHandler(v -> {
        req.response().putHeader("content-type", "text/plain").end("");
        Assert.assertEquals(expected, postContent.toString());
        testComplete();
      });
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, POST("/").add("content-encoding", "gzip"), 0, false, request.context.newPromise());
      request.encoder.writeData(request.context, id, BufferInternal.buffer(expectedGzipped).getByteBuf(), 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void test100ContinueHandledManually() throws Exception {
    server.requestHandler(req -> {
      Assert.assertEquals("100-continue", req.getHeader("expect"));
      HttpServerResponse resp = req.response();
      resp.writeContinue();
      req.bodyHandler(body -> {
        Assert.assertEquals("the-body", body.toString());
        resp.putHeader("wibble", "wibble-value").end();
      });
    });
    test100Continue();
  }

  @Test
  public void test100ContinueHandledAutomatically() throws Exception {
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions(serverOptions).setHandle100ContinueAutomatically(true));
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      req.bodyHandler(body -> {
        Assert.assertEquals("the-body", body.toString());
        resp.putHeader("wibble", "wibble-value").end();
      });
    });
    test100Continue();
  }

  private void test100Continue() throws Exception {
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        int count = 0;
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          switch (count++) {
            case 0:
              vertx.runOnContext(v -> {
                Assert.assertEquals("100", headers.status().toString());
              });
              request.encoder.writeData(request.context, id, BufferInternal.buffer("the-body").getByteBuf(), 0, true, request.context.newPromise());
              request.context.flush();
              break;
            case 1:
              vertx.runOnContext(v -> {
                Assert.assertEquals("200", headers.status().toString());
                Assert.assertEquals("wibble-value", headers.get("wibble").toString());
                testComplete();
              });
              break;
            default:
              vertx.runOnContext(v -> {
                Assert.fail();
              });
          }
        }
      });
      request.encoder.writeHeaders(request.context, id, GET("/").add("expect", "100-continue"), 0, false, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void test100ContinueRejectedManually() throws Exception {
    server.requestHandler(req -> {
      req.response().setStatusCode(405).end();
      req.handler(buf -> {
        Assert.fail();
      });
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        int count = 0;
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          switch (count++) {
            case 0:
              vertx.runOnContext(v -> {
                Assert.assertEquals("405", headers.status().toString());
                vertx.setTimer(100, v2 -> {
                  testComplete();
                });
              });
              break;
            default:
              vertx.runOnContext(v -> {
                Assert.fail();
              });
          }
        }
      });
      request.encoder.writeHeaders(request.context, id, GET("/").add("expect", "100-continue"), 0, false, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testNetSocketConnect() throws Exception {
    waitFor(4);

    server.requestHandler(req -> {
      req.toNetSocket().onComplete(TestUtils.onSuccess(socket -> {
        AtomicInteger status = new AtomicInteger();
        socket.handler(buff -> {
          switch (status.getAndIncrement()) {
            case 0:
              Assert.assertEquals(Buffer.buffer("some-data"), buff);
              socket.write(buff).onComplete(TestUtils.onSuccess(v2 -> complete()));
              break;
            case 1:
              Assert.assertEquals(Buffer.buffer("last-data"), buff);
              break;
            default:
              Assert.fail();
              break;
          }
        });
        socket.endHandler(v1 -> {
          Assert.assertEquals(2, status.getAndIncrement());
          socket.end(Buffer.buffer("last-data")).onComplete(TestUtils.onSuccess(v2 -> complete()));
        });
        socket.closeHandler(v -> complete());
      }));
    });

    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            Assert.assertEquals("200", headers.status().toString());
            Assert.assertFalse(endStream);
          });
          request.encoder.writeData(request.context, id, BufferInternal.buffer("some-data").getByteBuf(), 0, false, request.context.newPromise());
          request.context.flush();
        }
        StringBuilder received = new StringBuilder();
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          String s = data.toString(StandardCharsets.UTF_8);
          received.append(s);
          if (received.toString().equals("some-data")) {
            received.setLength(0);
            vertx.runOnContext(v -> {
              Assert.assertFalse(endOfStream);
            });
            request.encoder.writeData(request.context, id, BufferInternal.buffer("last-data").getByteBuf(), 0, true, request.context.newPromise());
          } else if (endOfStream) {
            vertx.runOnContext(v -> {
              Assert.assertEquals("last-data", received.toString());
              complete();
            });
          }
          return data.readableBytes() + padding;
        }
      });
      request.encoder.writeHeaders(request.context, id, new DefaultHttp2Headers().method("CONNECT").authority("example.com:80"), 0, false, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  @DetectFileDescriptorLeaks
  public void testNetSocketSendFile() throws Exception {
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(1000 * 1000));
    File tmp = createTempFile(expected);
    testNetSocketSendFile(expected, tmp.getAbsolutePath(), 0, expected.length());
  }

  @Test
  public void testNetSocketSendFileRange() throws Exception {
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(1000 * 1000));
    File tmp = createTempFile(expected);
    int from = 200 * 1000;
    int to = 700 * 1000;
    testNetSocketSendFile(expected.slice(from, to), tmp.getAbsolutePath(), from, to - from);
  }


  private void testNetSocketSendFile(Buffer expected, String path, long offset, long length) throws Exception {
    server.requestHandler(req -> {
      req.toNetSocket().onComplete(TestUtils.onSuccess(socket -> {
        socket.sendFile(path, offset, length).onComplete(TestUtils.onSuccess(v -> {
          socket.end();
        }));
      }));
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            Assert.assertEquals("200", headers.status().toString());
            Assert.assertFalse(endStream);
          });
        }
        Buffer received = Buffer.buffer();
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          byte[] tmp = new byte[data.readableBytes()];
          data.getBytes(data.readerIndex(), tmp);
          received.appendBytes(tmp);
          if (endOfStream) {
            vertx.runOnContext(v -> {
              Assert.assertEquals(received, expected);
              testComplete();
            });
          }
          return data.readableBytes() + padding;
        }
      });
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testServerCloseNetSocket() throws Exception {
    waitFor(2);
    final AtomicInteger writeAcks = new AtomicInteger(0);
    AtomicInteger status = new AtomicInteger();
    server.requestHandler(req -> {
      req.toNetSocket().onComplete(TestUtils.onSuccess(socket -> {
        socket.handler(buff -> {
          switch (status.getAndIncrement()) {
            case 0:
              Assert.assertEquals(Buffer.buffer("some-data"), buff);
              socket.write(buff).onComplete(TestUtils.onSuccess(v -> writeAcks.incrementAndGet()));
              socket.close();
              break;
            case 1:
              Assert.assertEquals(Buffer.buffer("last-data"), buff);
              break;
            default:
              Assert.fail();
              break;
          }
        });
        socket.endHandler(v -> {
          Assert.assertEquals(2, status.getAndIncrement());
        });
        socket.closeHandler(v -> {
          Assert.assertEquals(3, status.getAndIncrement());
          complete();
          Assert.assertEquals(1, writeAcks.get());
        });
      }));
    });

    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        int count = 0;
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          int c = count++;
          vertx.runOnContext(v -> {
            Assert.assertEquals(0, c);
          });
          request.encoder.writeData(request.context, id, BufferInternal.buffer("some-data").getByteBuf(), 0, false, request.context.newPromise());
          request.context.flush();
        }
        StringBuilder received = new StringBuilder();
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          String s = data.toString(StandardCharsets.UTF_8);
          received.append(s);
          if (endOfStream) {
            request.encoder.writeData(request.context, id, BufferInternal.buffer("last-data").getByteBuf(), 0, true, request.context.newPromise());
            vertx.runOnContext(v -> {
              Assert.assertEquals("some-data", received.toString());
              complete();
            });
          }
          return data.readableBytes() + padding;
        }
      });
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testNetSocketHandleReset() throws Exception {
    server.requestHandler(req -> {
      req.toNetSocket().onComplete(TestUtils.onSuccess(socket -> {
        AtomicInteger status = new AtomicInteger();
        socket.exceptionHandler(err -> {
          if (err instanceof StreamResetException) {
            Assert.assertEquals(0, status.getAndIncrement());
            StreamResetException ex = (StreamResetException) err;
            Assert.assertEquals(0, ex.getCode());
          }
        });
        socket.endHandler(v -> {
          // fail();
        });
        socket.closeHandler(v  -> {
          Assert.assertEquals(1, status.getAndIncrement());
          testComplete();
        });
      }));
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        int count = 0;
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          int c = count++;
          vertx.runOnContext(v -> {
            Assert.assertEquals(0, c);
          });
          request.encoder.writeRstStream(ctx, streamId, 0, ctx.newPromise());
          request.context.flush();
        }
      });
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testNetSocketPauseResume() throws Exception {
    testStreamPauseResume(req -> req.toNetSocket().map(so -> so));
  }

  @Test
  public void testNetSocketWritability() throws Exception {
    testStreamWritability(req -> req.toNetSocket().map(so -> so));
  }

  @Test
  public void testUnknownFrame() throws Exception {
    Buffer expectedSend = TestUtils.randomBuffer(500);
    Buffer expectedRecv = TestUtils.randomBuffer(500);
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      req.customFrameHandler(frame -> {
        TestUtils.assertOnIOContext(ctx);
        Assert.assertEquals(10, frame.type());
        Assert.assertEquals(253, frame.flags());
        Assert.assertEquals(expectedSend, frame.payload());
        HttpServerResponse resp = req.response();
        resp.writeCustomFrame(12, 134, expectedRecv);
        resp.end();
      });
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        int status = 0;
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          int s = status++;
          vertx.runOnContext(v -> {
            Assert.assertEquals(0, s);
          });
        }
        @Override
        public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf payload) {
          int s = status++;
          byte[] tmp = new byte[payload.readableBytes()];
          payload.getBytes(payload.readerIndex(), tmp);
          Buffer recv = Buffer.buffer().appendBytes(tmp);
          vertx.runOnContext(v -> {
            Assert.assertEquals(1, s);
            Assert.assertEquals(12, frameType);
            Assert.assertEquals(134, flags.value());
            Assert.assertEquals(expectedRecv, recv);
          });
        }
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          int len = data.readableBytes();
          int s = status++;
          vertx.runOnContext(v -> {
            Assert.assertEquals(2, s);
            Assert.assertEquals(0, len);
            Assert.assertTrue(endOfStream);
            testComplete();
          });
          return data.readableBytes() + padding;
        }
      });
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      request.encoder.writeFrame(request.context, (byte)10, id, new Http2Flags((short) 253), ((BufferInternal)expectedSend).getByteBuf(), request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testUpgradeToClearTextGet() throws Exception {
    testUpgradeToClearText(HttpMethod.GET, Buffer.buffer(), options -> {});
  }

  @Test
  public void testUpgradeToClearTextPut() throws Exception {
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(20));
    testUpgradeToClearText(HttpMethod.PUT, expected, options -> {});
  }

  @Test
  public void testUpgradeToClearTextWithCompression() throws Exception {
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(8192));
    testUpgradeToClearText(HttpMethod.PUT, expected, options -> options.setCompressionSupported(true));
  }

  @Test
  public void testUpgradeToClearTextInvalidHost() throws Exception {
    testUpgradeToClearText(new RequestOptions(requestOptions).putHeader("Host", "localhost:not"), options -> {})
      .compose(req -> req.send()).onComplete(TestUtils.onFailure(failure -> {
        // Regression
        Assert.assertEquals(StreamResetException.class, failure.getClass());
        Assert.assertEquals(1L, ((StreamResetException)failure).getCode());
        testComplete();
      }));
    await();
  }

  private void testUpgradeToClearText(HttpMethod method, Buffer expected, Handler<HttpServerOptions> optionsConfig) throws Exception {
    Future<HttpClientRequest> fut = testUpgradeToClearText(new RequestOptions(requestOptions).setMethod(method), optionsConfig);
    fut.compose(req -> req.send(expected)
      .andThen(TestUtils.onSuccess(resp -> {
        Assert.assertEquals(200, resp.statusCode());
        Assert.assertEquals(HttpVersion.HTTP_2, resp.version());
      }))
      .compose(resp -> resp.body())).onComplete(TestUtils.onSuccess(body -> {
      Assert.assertEquals(expected, body);
      testComplete();
    }));
    await();
  }

  private Future<HttpClientRequest> testUpgradeToClearText(RequestOptions request,
                                      Handler<HttpServerOptions> optionsConfig) throws Exception {
    server.close();
    optionsConfig.handle(serverOptions);
    server = vertx.createHttpServer(new HttpServerOptions(serverOptions)
      .setHost(DEFAULT_HTTP_HOST)
      .setPort(DEFAULT_HTTP_PORT)
      .setUseAlpn(false)
      .setSsl(false)
      .setInitialSettings(new io.vertx.core.http.Http2Settings().setMaxConcurrentStreams(20000)));
    server.requestHandler(req -> {
      Assert.assertEquals("http", req.scheme());
      Assert.assertEquals(request.getMethod(), req.method());
      Assert.assertEquals(HttpVersion.HTTP_2, req.version());
      io.vertx.core.http.HttpSettings remoteSettings = req.connection().remoteSettings();
      Assert.assertEquals(10000L, (long)remoteSettings.get(io.vertx.core.http.Http2Settings.MAX_CONCURRENT_STREAMS));
      Assert.assertFalse(req.isSSL());
      req.bodyHandler(body -> {
        vertx.setTimer(10, id -> {
          req.response().end(body);
        });
      });
    }).connectionHandler(conn -> {
      Assert.assertNotNull(conn);
    });
    startServer(testAddress);
    client = vertx.createHttpClient(clientOptions.
        setUseAlpn(false).
        setSsl(false).
        setInitialSettings(new io.vertx.core.http.Http2Settings().setMaxConcurrentStreams(10000)));
    return client.request(request);
  }

  @Test
  public void testUpgradeToClearTextIdleTimeout() throws Exception {
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions(serverOptions)
      .setHost(DEFAULT_HTTP_HOST)
      .setPort(DEFAULT_HTTP_PORT)
      .setUseAlpn(false)
      .setSsl(false)
      .setIdleTimeout(250)
      .setIdleTimeoutUnit(TimeUnit.MILLISECONDS));
    server.requestHandler(req -> {
      req.connection().closeHandler(v -> {
        testComplete();
      });
    });
    startServer(testAddress);
    client = vertx.createHttpClient(clientOptions.
      setUseAlpn(false).
      setSsl(false));
    client.request(requestOptions).compose(request -> request.send());
    await();
  }

  @Test
  public void testPushPromiseClearText() throws Exception {
    waitFor(2);
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions(serverOptions).
        setHost(DEFAULT_HTTP_HOST).
        setPort(DEFAULT_HTTP_PORT).
        setUseAlpn(false).
        setSsl(false));
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/resource").onComplete(TestUtils.onSuccess(resp -> {
        resp.end("the-pushed-response");
      }));
      req.response().end();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(clientOptions.setUseAlpn(false).setSsl(false));
    client.request(requestOptions).onComplete(TestUtils.onSuccess(req -> {
      req.exceptionHandler(err -> Assert.fail(err.getMessage())).pushHandler(pushedReq -> {
        pushedReq.response().onComplete(TestUtils.onSuccess(pushResp -> {
          pushResp.bodyHandler(buff -> {
            Assert.assertEquals("the-pushed-response", buff.toString());
            complete();
          });
        }));
      }).send().onComplete(TestUtils.onSuccess(resp -> {
        Assert.assertEquals(HttpVersion.HTTP_2, resp.version());
        complete();
      }));
    }));
    await();
  }

  @Test
  public void testUpgradeToClearTextInvalidConnectionHeader() throws Exception {
    Assume.assumeFalse(serverOptions.getHttp2MultiplexImplementation());
    testUpgradeFailure(vertx.getOrCreateContext(), (client, handler) -> {
      client.request(new RequestOptions()
        .setPort(DEFAULT_HTTP_PORT)
        .setHost(DEFAULT_HTTP_HOST)
        .setURI("/somepath")).onComplete(TestUtils.onSuccess(req -> {
          req
            .putHeader("Upgrade", "h2c")
            .putHeader("Connection", "Upgrade")
            .putHeader("HTTP2-Settings", HttpUtils.encodeSettings(new io.vertx.core.http.Http2Settings()))
            .send()
            .onComplete(handler);
        }));
    });
  }

  @Test
  public void testUpgradeToClearTextMalformedSettings() throws Exception {
    Assume.assumeFalse(serverOptions.getHttp2MultiplexImplementation());
    testUpgradeFailure(vertx.getOrCreateContext(), (client, handler) -> {
      client.request(new RequestOptions()
        .setPort(DEFAULT_HTTP_PORT)
        .setHost(DEFAULT_HTTP_HOST)
        .setURI("/somepath")).onComplete(TestUtils.onSuccess(req -> {
          req
            .putHeader("Upgrade", "h2c")
            .putHeader("Connection", "Upgrade, HTTP2-Settings")
            .putHeader("HTTP2-Settings", "incorrect-settings")
            .send()
            .onComplete(handler);
        }));
    });
  }

  @Test
  public void testUpgradeToClearTextInvalidSettings() throws Exception {
    Assume.assumeFalse(serverOptions.getHttp2MultiplexImplementation());
    Buffer buffer = Buffer.buffer();
    buffer.appendUnsignedShort(5).appendUnsignedInt((0xFFFFFF + 1));
    String s = new String(Base64.getUrlEncoder().encode(buffer.getBytes()), StandardCharsets.UTF_8);
    testUpgradeFailure(vertx.getOrCreateContext(), (client, handler) -> {
      client.request(new RequestOptions()
        .setPort(DEFAULT_HTTP_PORT)
        .setHost(DEFAULT_HTTP_HOST)
        .setURI("/somepath")).onComplete(TestUtils.onSuccess(req -> {
          req
            .putHeader("Upgrade", "h2c")
            .putHeader("Connection", "Upgrade, HTTP2-Settings")
            .putHeader("HTTP2-Settings", s)
            .send()
            .onComplete(handler);
      }));
    });
  }

  @Test
  public void testUpgradeToClearTextMissingSettings() throws Exception {
    Assume.assumeFalse(serverOptions.getPerMessageWebSocketCompressionSupported());
    testUpgradeFailure(vertx.getOrCreateContext(), (client, handler) -> {
      client.request(new RequestOptions()
        .setPort(DEFAULT_HTTP_PORT)
        .setHost(DEFAULT_HTTP_HOST)
        .setURI("/somepath")).onComplete(TestUtils.onSuccess(req -> {
        req
          .putHeader("Upgrade", "h2c")
          .putHeader("Connection", "Upgrade, HTTP2-Settings")
          .send()
          .onComplete(handler);
      }));
    });
  }

  @Test
  public void testUpgradeToClearTextWorkerContext() throws Exception {
    Assume.assumeFalse(serverOptions.getHttp2MultiplexImplementation());
    testUpgradeFailure(vertx.getOrCreateContext(), (client, handler) -> {
      client.request(new RequestOptions()
        .setPort(DEFAULT_HTTP_PORT)
        .setHost(DEFAULT_HTTP_HOST)
        .setURI("/somepath")).onComplete(TestUtils.onSuccess(req -> {
          req
            .putHeader("Upgrade", "h2c")
            .putHeader("Connection", "Upgrade")
            .putHeader("HTTP2-Settings", HttpUtils.encodeSettings(new io.vertx.core.http.Http2Settings()))
            .send().onComplete(handler);
        }));
    });
  }

  private void testUpgradeFailure(Context context, BiConsumer<HttpClient, Handler<AsyncResult<HttpClientResponse>>> doRequest) throws Exception {
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions(serverOptions).setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT).setUseAlpn(false).setSsl(false));
    server.requestHandler(req -> {
      Assert.fail();
    });
    startServer(context);
    client.close();
    client = vertx.createHttpClient(clientOptions.setProtocolVersion(HttpVersion.HTTP_1_1).setUseAlpn(false).setSsl(false));
    doRequest.accept(client, TestUtils.onSuccess(resp -> {
      Assert.assertEquals(400, resp.statusCode());
      Assert.assertEquals(HttpVersion.HTTP_1_1, resp.version());
      testComplete();
    }));
    await();
  }

  @Test
  public void testUpgradeToClearTextPartialFailure() throws Exception {
    Assume.assumeFalse(serverOptions.getHttp2MultiplexImplementation());
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions(serverOptions).setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT).setUseAlpn(false).setSsl(false));
    CompletableFuture<Void> closeRequest = new CompletableFuture<>();
    server.requestHandler(req -> {
      closeRequest.complete(null);
      AtomicBoolean processed = new AtomicBoolean();
      req.exceptionHandler(err -> {
        if (processed.compareAndSet(false, true)) {
          testComplete();
        }
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(clientOptions.setProtocolVersion(HttpVersion.HTTP_1_1).setUseAlpn(false).setSsl(false));
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT)).onComplete(TestUtils.onSuccess(req -> {
      req
        .putHeader("Upgrade", "h2c")
        .putHeader("Connection", "Upgrade,HTTP2-Settings")
        .putHeader("HTTP2-Settings", HttpUtils.encodeSettings(new io.vertx.core.http.Http2Settings()))
        .setChunked(true);
      req.write("some-data");
      closeRequest.thenAccept(v -> {
        req.connection().close();
      });
    }));
    await();
  }

  @Test
  public void testIdleTimeout() throws Exception {
    waitFor(4);
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions(serverOptions).setIdleTimeoutUnit(TimeUnit.MILLISECONDS).setIdleTimeout(2000));
    server.requestHandler(req -> {
      req.exceptionHandler(err -> {
        Assert.assertTrue(err instanceof HttpClosedException);
        complete();
      });
      req.response().closeHandler(v -> {
        complete();
      });
      req.response().endHandler(v -> {
        Assert.fail();
      });
      req.connection().closeHandler(v -> {
        complete();
      });
    });
    startServer();
    TestClient client = new TestClient();
    Channel channel = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
      });
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      request.context.flush();
    });
    channel.closeFuture().addListener(v1 -> {
      vertx.runOnContext(v2 -> {
        complete();
      });
    });
    await();
  }

  @Test
  public void testSendPing() throws Exception {
    waitFor(2);
    Buffer expected = TestUtils.randomBuffer(8);
    Context ctx = vertx.getOrCreateContext();
    server.connectionHandler(conn -> {
      conn.ping(expected).onComplete(TestUtils.onSuccess(res -> {
        Assert.assertSame(ctx, Vertx.currentContext());
        Assert.assertEquals(expected, res);
        complete();
      }));
    });
    server.requestHandler(req -> Assert.fail());
    startServer(ctx);
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
          Buffer buffer = Buffer.buffer().appendLong(data);
          vertx.runOnContext(v -> {
            Assert.assertEquals(expected, buffer);
            complete();
          });
        }
      });
    });
    await();
  }

  @Test
  public void testReceivePing() throws Exception {
    Buffer expected = TestUtils.randomBuffer(8);
    Context ctx = vertx.getOrCreateContext();
    server.connectionHandler(conn -> {
      conn.pingHandler(buff -> {
        TestUtils.assertOnIOContext(ctx);
        Assert.assertEquals(expected, buff);
        testComplete();
      });
    });
    server.requestHandler(req -> Assert.fail());
    startServer(ctx);
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.encoder.writePing(request.context, false, expected.getLong(0), request.context.newPromise());
    });
    await();
  }

  @Test
  public void testPriorKnowledge() throws Exception {
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions(serverOptions)
        .setSsl(false)
        .setPort(DEFAULT_HTTP_PORT)
        .setHost(DEFAULT_HTTP_HOST)
    );
    server.requestHandler(req -> {
      req.response().end("Hello World");
    });
    startServer();
    TestClient client = new TestClient() {
      @Override
      protected ChannelInitializer channelInitializer(int port, String host, Promise<SslHandshakeCompletionEvent> latch, Consumer<Connection> handler) {
        return new ChannelInitializer() {
          @Override
          protected void initChannel(Channel ch) throws Exception {
            ChannelPipeline p = ch.pipeline();
            Http2Connection connection = new DefaultHttp2Connection(false);
            TestClientHandlerBuilder clientHandlerBuilder = new TestClientHandlerBuilder(handler);
            TestClientHandler clientHandler = clientHandlerBuilder.build(connection);
            p.addLast(clientHandler);
            latch.complete(SslHandshakeCompletionEvent.SUCCESS);
          }
        };
      }
    };
    client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            testComplete();
          });
        }
      });
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    await();
  }

  @Test
  public void testConnectionWindowSize() throws Exception {
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions(serverOptions).setHttp2ConnectionWindowSize(65535 + 65535));
    server.requestHandler(req  -> {
      req.response().end();
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) throws Http2Exception {
          vertx.runOnContext(v -> {
            Assert.assertEquals(65535, windowSizeIncrement);
            testComplete();
          });
        }
      });
    });
    await();
  }

  @Test
  public void testUpdateConnectionWindowSize() throws Exception {
    server.connectionHandler(conn -> {
      Assert.assertEquals(65535, conn.getWindowSize());
      conn.setWindowSize(65535 + 10000);
      Assert.assertEquals(65535 + 10000, conn.getWindowSize());
      conn.setWindowSize(65535 + 65535);
      Assert.assertEquals(65535 + 65535, conn.getWindowSize());
    }).requestHandler(req  -> {
      req.response().end();
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) throws Http2Exception {
          vertx.runOnContext(v -> {
            Assert.assertEquals(65535, windowSizeIncrement);
            testComplete();
          });
        }
      });
    });
    await();
  }

  class TestHttp1xOrH2CHandler extends Http1xOrH2CHandler {

    @Override
    protected void configure(ChannelHandlerContext ctx, boolean h2c) {
      if (h2c) {
        ChannelPipeline p = ctx.pipeline();
        p.addLast(new ChannelDuplexHandler() {
          @Override
          public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ctx.write(msg);
          }
          @Override
          public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.flush();
          }
        });
      } else {
        ChannelPipeline p = ctx.pipeline();
        p.addLast(new HttpServerCodec());
        p.addLast(new ChannelDuplexHandler() {
          @Override
          public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ctx.write(msg);
          }
          @Override
          public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.flush();
          }
        });
      }
    }
  }

  private static final ByteBuf HTTP_1_1_POST = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("POST /whatever HTTP/1.1\r\n\r\n", StandardCharsets.UTF_8));

  @Test
  public void testHttp1xOrH2CHandlerHttp1xRequest() throws Exception {
    EmbeddedChannel ch =  new EmbeddedChannel(new TestHttp1xOrH2CHandler());
    ByteBuf buff = HTTP_1_1_POST.copy(0, HTTP_1_1_POST.readableBytes());
    ch.writeInbound(buff);
    Assert.assertEquals(0, buff.refCnt());
    Assert.assertEquals(1, ch.outboundMessages().size());
    HttpRequest req = (HttpRequest) ch.outboundMessages().poll();
    Assert.assertEquals("POST", req.method().name());
    Assert.assertNull(ch.pipeline().get(TestHttp1xOrH2CHandler.class));
  }

  @Test
  public void testHttp1xOrH2CHandlerFragmentedHttp1xRequest() throws Exception {
    EmbeddedChannel ch =  new EmbeddedChannel(new TestHttp1xOrH2CHandler());
    ByteBuf buff = HTTP_1_1_POST.copy(0, 1);
    ch.writeInbound(buff);
    Assert.assertEquals(0, buff.refCnt());
    Assert.assertEquals(0, ch.outboundMessages().size());
    buff = HTTP_1_1_POST.copy(1, HTTP_1_1_POST.readableBytes() - 1);
    ch.writeInbound(buff);
    Assert.assertEquals(0, buff.refCnt());
    Assert.assertEquals(1, ch.outboundMessages().size());
    HttpRequest req = (HttpRequest) ch.outboundMessages().poll();
    Assert.assertEquals("POST", req.method().name());
    Assert.assertNull(ch.pipeline().get(TestHttp1xOrH2CHandler.class));
  }

  @Test
  public void testHttp1xOrH2CHandlerHttp2Request() throws Exception {
    EmbeddedChannel ch =  new EmbeddedChannel(new TestHttp1xOrH2CHandler());
    ByteBuf expected = Unpooled.copiedBuffer(Http1xOrH2CHandler.HTTP_2_PREFACE, StandardCharsets.UTF_8);
    ch.writeInbound(expected);
    Assert.assertEquals(1, expected.refCnt());
    Assert.assertEquals(1, ch.outboundMessages().size());
    ByteBuf res = (ByteBuf) ch.outboundMessages().poll();
    Assert.assertEquals(Http1xOrH2CHandler.HTTP_2_PREFACE, res.toString(StandardCharsets.UTF_8));
    Assert.assertNull(ch.pipeline().get(TestHttp1xOrH2CHandler.class));
  }

  @Test
  public void testHttp1xOrH2CHandlerFragmentedHttp2Request() throws Exception {
    EmbeddedChannel ch =  new EmbeddedChannel(new TestHttp1xOrH2CHandler());
    ByteBuf expected = Unpooled.copiedBuffer(Http1xOrH2CHandler.HTTP_2_PREFACE, StandardCharsets.UTF_8);
    ByteBuf buff = expected.copy(0, 1);
    ch.writeInbound(buff);
    Assert.assertEquals(0, buff.refCnt());
    Assert.assertEquals(0, ch.outboundMessages().size());
    buff = expected.copy(1, expected.readableBytes() - 1);
    ch.writeInbound(buff);
    Assert.assertEquals(0, buff.refCnt());
    Assert.assertEquals(1, ch.outboundMessages().size());
    ByteBuf res = (ByteBuf) ch.outboundMessages().poll();
    Assert.assertEquals(1, res.refCnt());
    Assert.assertEquals(Http1xOrH2CHandler.HTTP_2_PREFACE, res.toString(StandardCharsets.UTF_8));
    Assert.assertNull(ch.pipeline().get(TestHttp1xOrH2CHandler.class));
  }


  @Test
  public void testStreamPriority() throws Exception {
    StreamPriority requestStreamPriority = new StreamPriority().setDependency(123).setWeight((short)45).setExclusive(true);
    StreamPriority responseStreamPriority = new StreamPriority().setDependency(153).setWeight((short)75).setExclusive(false);
    waitFor(4);
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      Assert.assertEquals(requestStreamPriority, req.streamPriority());
      resp.setStatusCode(200);
      resp.setStreamPriority(responseStreamPriority);
      resp.end("data");
      complete();
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), requestStreamPriority.getDependency(), requestStreamPriority.getWeight(), requestStreamPriority.isExclusive(), 0, true, request.context.newPromise());
      request.context.flush();
      request.decoder.frameListener(new Http2FrameAdapter() {
          @Override
          public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding,
                boolean endStream) throws Http2Exception {
            vertx.runOnContext(v -> {
                Assert.assertEquals(id, streamId);
                Assert.assertEquals(responseStreamPriority.getDependency(), streamDependency);
                Assert.assertEquals(responseStreamPriority.getWeight(), weight);
                Assert.assertEquals(responseStreamPriority.isExclusive(), exclusive);
                complete();
              });
          }
          @Override
          public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) throws Http2Exception {
            vertx.runOnContext(v -> {
              Assert.assertEquals(id, streamId);
              Assert.assertEquals(responseStreamPriority.getDependency(), streamDependency);
              Assert.assertEquals(responseStreamPriority.getWeight(), weight);
              Assert.assertEquals(responseStreamPriority.isExclusive(), exclusive);
              complete();
            });
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
    });
    await();
  }

  @Test
  public void testStreamPriorityChange() throws Exception {
    StreamPriority requestStreamPriority = new StreamPriority().setDependency(123).setWeight((short) 45).setExclusive(true);
    StreamPriority requestStreamPriority2 = new StreamPriority().setDependency(223).setWeight((short) 145).setExclusive(false);
    StreamPriority responseStreamPriority = new StreamPriority().setDependency(153).setWeight((short) 75).setExclusive(false);
    StreamPriority responseStreamPriority2 = new StreamPriority().setDependency(253).setWeight((short) 175).setExclusive(true);
    waitFor(6);
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      Assert.assertEquals(requestStreamPriority, req.streamPriority());
      req.bodyHandler(b -> {
          Assert.assertEquals(requestStreamPriority2, req.streamPriority());
          resp.setStatusCode(200);
          resp.setStreamPriority(responseStreamPriority);
          resp.write("hello");
          resp.setStreamPriority(responseStreamPriority2);
          resp.end("world");
          complete();
      });
      req.streamPriorityHandler(streamPriority -> {
          Assert.assertEquals(requestStreamPriority2, streamPriority);
          Assert.assertEquals(requestStreamPriority2, req.streamPriority());
          complete();
      });
    });
    startServer();
    TestClient client = new TestClient();
    Context context = vertx.getOrCreateContext();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), requestStreamPriority.getDependency(), requestStreamPriority.getWeight(), requestStreamPriority.isExclusive(), 0, false, request.context.newPromise());
      request.context.flush();
      request.encoder.writePriority(request.context, id, requestStreamPriority2.getDependency(), requestStreamPriority2.getWeight(), requestStreamPriority2.isExclusive(), request.context.newPromise());
      request.context.flush();
      request.encoder.writeData(request.context, id, BufferInternal.buffer("hello").getByteBuf(), 0, true, request.context.newPromise());
      request.context.flush();
      request.decoder.frameListener(new Http2FrameAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding,
              boolean endStream) throws Http2Exception {
          super.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream);
          context.runOnContext(v -> {
            Assert.assertEquals(id, streamId);
            Assert.assertEquals(responseStreamPriority.getDependency(), streamDependency);
            Assert.assertEquals(responseStreamPriority.getWeight(), weight);
            Assert.assertEquals(responseStreamPriority.isExclusive(), exclusive);
            complete();
          });
        }
        int cnt;
        @Override
        public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) throws Http2Exception {
          context.runOnContext(v -> {
            Assert.assertEquals(id, streamId);
            switch (cnt++) {
              case 0:
                Assert.assertEquals(responseStreamPriority.getDependency(), streamDependency); // HERE
                Assert.assertEquals(responseStreamPriority.getWeight(), weight);
                Assert.assertEquals(responseStreamPriority.isExclusive(), exclusive);
                complete();
                break;
              case 1:
                Assert.assertEquals(responseStreamPriority2.getDependency(), streamDependency);
                Assert.assertEquals(responseStreamPriority2.getWeight(), weight);
                Assert.assertEquals(responseStreamPriority2.isExclusive(), exclusive);
                complete();
                break;
              default:
                Assert.fail();
                break;
            }
          });
        }
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
            if(endOfStream) {
              context.runOnContext(v -> {
                complete();
              });
            }
            return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
    });
    await();
  }

  @Test
  public void testStreamPriorityNoChange() throws Exception {
    StreamPriority requestStreamPriority = new StreamPriority().setDependency(123).setWeight((short)45).setExclusive(true);
    StreamPriority responseStreamPriority = new StreamPriority().setDependency(153).setWeight((short)75).setExclusive(false);
    waitFor(4);
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      Assert.assertEquals(requestStreamPriority, req.streamPriority());
      req.bodyHandler(b -> {
        Assert.assertEquals(requestStreamPriority, req.streamPriority());
        resp.setStatusCode(200);
        resp.setStreamPriority(responseStreamPriority);
        resp.write("hello");
        resp.setStreamPriority(responseStreamPriority);
        resp.end("world");
        complete();
      });
      req.streamPriorityHandler(streamPriority -> {
        Assert.fail("Stream priority handler should not be called");
      });
    });
    startServer();
    TestClient client = new TestClient();
    client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), requestStreamPriority.getDependency(), requestStreamPriority.getWeight(), requestStreamPriority.isExclusive(), 0, false, request.context.newPromise());
      request.context.flush();
      request.encoder.writePriority(request.context, id, requestStreamPriority.getDependency(), requestStreamPriority.getWeight(), requestStreamPriority.isExclusive(), request.context.newPromise());
      request.context.flush();
      request.encoder.writeData(request.context, id, BufferInternal.buffer("hello").getByteBuf(), 0, true, request.context.newPromise());
      request.context.flush();
      request.decoder.frameListener(new Http2FrameAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding,
              boolean endStream) throws Http2Exception {
          super.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream);
          vertx.runOnContext(v -> {
            Assert.assertEquals(id, streamId);
            Assert.assertEquals(responseStreamPriority.getDependency(), streamDependency);
            Assert.assertEquals(responseStreamPriority.getWeight(), weight);
            Assert.assertEquals(responseStreamPriority.isExclusive(), exclusive);
            complete();
          });
        }
        @Override
        public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) throws Http2Exception {
          vertx.runOnContext(v -> {
            Assert.assertEquals(id, streamId);
            Assert.assertEquals(responseStreamPriority.getDependency(), streamDependency);
            Assert.assertEquals(responseStreamPriority.getWeight(), weight);
            Assert.assertEquals(responseStreamPriority.isExclusive(), exclusive);
            complete();
          });
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
    });
    await();
  }


}
