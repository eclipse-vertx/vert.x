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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
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
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.KeyStoreHelper;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
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

    public final Http2Settings settings = new Http2Settings();

    public class Request {
      public final Channel channel;
      public final ChannelHandlerContext context;
      public final Http2Connection connection;
      public final Http2ConnectionEncoder encoder;
      public final Http2ConnectionDecoder decoder;

      public Request(Channel channel, ChannelHandlerContext context, Http2Connection connection, Http2ConnectionEncoder encoder, Http2ConnectionDecoder decoder) {
        this.channel = channel;
        this.context = context;
        this.connection = connection;
        this.encoder = encoder;
        this.decoder = decoder;
      }

      public int nextStreamId() {
        return connection.local().incrementAndGetNextStreamId();
      }
    }

    public ChannelFuture connect(int port, String host, Consumer<Request> handler) {

      class TestClientHandler extends Http2ConnectionHandler {
        public TestClientHandler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) {
          super(decoder, encoder, initialSettings);
        }
      }

      class TestClientHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<TestClientHandler, TestClientHandlerBuilder> {
        @Override
        protected TestClientHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) throws Exception {
          return new TestClientHandler(decoder, encoder, initialSettings);
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

      Bootstrap bootstrap = new Bootstrap();
      bootstrap.channel(NioSocketChannel.class);
      bootstrap.group(new NioEventLoopGroup());
      bootstrap.handler(new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel ch) throws Exception {
          SSLHelper sslHelper = new SSLHelper(new HttpClientOptions().setUseAlpn(true), null, KeyStoreHelper.create((VertxInternal) vertx, TLSCert.JKS.getClientTrustOptions()));
          SslHandler sslHandler = sslHelper.setApplicationProtocols(Arrays.asList(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1)).createSslHandler((VertxInternal) vertx, host, port);
          ch.pipeline().addLast(sslHandler);
          ch.pipeline().addLast(new ApplicationProtocolNegotiationHandler("whatever") {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
              if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                ChannelPipeline p = ctx.pipeline();
                Http2Connection connection = new DefaultHttp2Connection(false);
                TestClientHandlerBuilder clientHandlerBuilder = new TestClientHandlerBuilder();
                TestClientHandler clientHandler = clientHandlerBuilder.build(connection);
                p.addLast(clientHandler);
                Request request = new Request(ch, ctx, connection, clientHandler.encoder(), clientHandler.decoder());
                handler.accept(request);
                return;
              }
              ctx.close();
              throw new IllegalStateException("unknown protocol: " + protocol);
            }
          });
        }
      });
      return bootstrap.connect(new InetSocketAddress(host, port));
    }
  }

  @Test
  public void testConnectionHandler() throws Exception {
    waitFor(2);
    Context ctx = vertx.getOrCreateContext();
    server.close();
    server.connectionHandler(conn -> {
      assertOnIOContext(ctx);
      complete();
    });
    server.requestHandler(req -> fail());
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      vertx.runOnContext(v -> {
        complete();
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testServerInitialSettings() throws Exception {
    io.vertx.core.http.Http2Settings settings = TestUtils.randomHttp2Settings();
    server.close();
    server = vertx.createHttpServer(serverOptions.setInitialSettings(settings));
    server.requestHandler(req -> fail());
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2FrameAdapter() {
        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings newSettings) throws Http2Exception {
          vertx.runOnContext(v -> {
            assertEquals((Long) settings.getHeaderTableSize(), newSettings.headerTableSize());
            assertEquals((Long) settings.getMaxConcurrentStreams(), newSettings.maxConcurrentStreams());
            assertEquals((Integer) settings.getInitialWindowSize(), newSettings.initialWindowSize());
            assertEquals((Integer) settings.getMaxFrameSize(), newSettings.maxFrameSize());
            assertEquals((Integer) settings.getMaxHeaderListSize(), newSettings.maxHeaderListSize());
            assertEquals(settings.get('\u0007'), newSettings.get('\u0007'));
            testComplete();
          });
        }
      });
    });
    fut.sync();
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
        conn.updateSettings(expectedSettings, ar -> {
          assertSame(otherContext, Vertx.currentContext());
          io.vertx.core.http.Http2Settings ackedSettings = conn.settings();
          assertEquals(expectedSettings.getMaxHeaderListSize(), ackedSettings.getMaxHeaderListSize());
          assertEquals(expectedSettings.getMaxFrameSize(), ackedSettings.getMaxFrameSize());
          assertEquals(expectedSettings.getInitialWindowSize(), ackedSettings.getInitialWindowSize());
          assertEquals(expectedSettings.getMaxConcurrentStreams(), ackedSettings.getMaxConcurrentStreams());
          assertEquals(expectedSettings.getHeaderTableSize(),  ackedSettings.getHeaderTableSize());
          assertEquals(expectedSettings.get('\u0007'), ackedSettings.get(7));
          complete();
        });
      });
    });
    server.requestHandler(req -> {
      fail();
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2FrameAdapter() {
        AtomicInteger count = new AtomicInteger();

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings newSettings) throws Http2Exception {
          vertx.runOnContext(v -> {
            switch (count.getAndIncrement()) {
              case 0:
                // Initial settings
                break;
              case 1:
                // Server sent settings
                assertEquals((Integer)expectedSettings.getMaxHeaderListSize(), newSettings.maxHeaderListSize());
                assertEquals((Integer)expectedSettings.getMaxFrameSize(), newSettings.maxFrameSize());
                assertEquals((Integer)expectedSettings.getInitialWindowSize(), newSettings.initialWindowSize());
                assertEquals((Long)expectedSettings.getMaxConcurrentStreams(), newSettings.maxConcurrentStreams());
                assertEquals(null, newSettings.headerTableSize());
                complete();
                break;
              default:
                fail();
            }
          });
        }
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testClientSettings() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    io.vertx.core.http.Http2Settings initialSettings = TestUtils.randomHttp2Settings();
    io.vertx.core.http.Http2Settings updatedSettings = TestUtils.randomHttp2Settings();
    Future<Void> settingsRead = Future.future();
    AtomicInteger count = new AtomicInteger();
    server.connectionHandler(conn -> {
      io.vertx.core.http.Http2Settings settings = conn.remoteSettings();
      assertEquals(true, settings.isPushEnabled());
      assertEquals(Integer.MAX_VALUE, settings.getMaxHeaderListSize());
      assertEquals(io.vertx.core.http.Http2Settings.DEFAULT_MAX_FRAME_SIZE, settings.getMaxFrameSize());
      assertEquals(io.vertx.core.http.Http2Settings.DEFAULT_INITIAL_WINDOW_SIZE, settings.getInitialWindowSize());
      assertEquals((Long)(long)Integer.MAX_VALUE, (Long)(long)settings.getMaxConcurrentStreams());
      assertEquals(io.vertx.core.http.Http2Settings.DEFAULT_HEADER_TABLE_SIZE, settings.getHeaderTableSize());
      conn.remoteSettingsHandler(update -> {
        assertOnIOContext(ctx);
        switch (count.getAndIncrement()) {
          case 0:
            assertEquals(initialSettings.isPushEnabled(), update.isPushEnabled());
            assertEquals(initialSettings.getMaxHeaderListSize(), update.getMaxHeaderListSize());
            assertEquals(initialSettings.getMaxFrameSize(), update.getMaxFrameSize());
            assertEquals(initialSettings.getInitialWindowSize(), update.getInitialWindowSize());
            assertEquals(initialSettings.getMaxConcurrentStreams(), update.getMaxConcurrentStreams());
            assertEquals(initialSettings.getHeaderTableSize(), update.getHeaderTableSize());
            assertEquals(initialSettings.get('\u0007'), update.get(7));
            settingsRead.complete();
            break;
          case 1:
            assertEquals(updatedSettings.isPushEnabled(), update.isPushEnabled());
            assertEquals(updatedSettings.getMaxHeaderListSize(), update.getMaxHeaderListSize());
            assertEquals(updatedSettings.getMaxFrameSize(), update.getMaxFrameSize());
            assertEquals(updatedSettings.getInitialWindowSize(), update.getInitialWindowSize());
            assertEquals(updatedSettings.getMaxConcurrentStreams(), update.getMaxConcurrentStreams());
            assertEquals(updatedSettings.getHeaderTableSize(), update.getHeaderTableSize());
            assertEquals(updatedSettings.get('\u0007'), update.get(7));
            testComplete();
            break;
        }
      });
    });
    server.requestHandler(req -> {
      fail();
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.settings.putAll(HttpUtils.fromVertxSettings(initialSettings));
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      settingsRead.setHandler(ar -> {
        request.encoder.writeSettings(request.context, HttpUtils.fromVertxSettings(updatedSettings), request.context.newPromise());
        request.context.flush();
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testGet() throws Exception {
    String expected = TestUtils.randomAlphaString(1000);
    AtomicBoolean requestEnded = new AtomicBoolean();
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      assertOnIOContext(ctx);
      req.endHandler(v -> {
        assertOnIOContext(ctx);
        requestEnded.set(true);
      });
      HttpServerResponse resp = req.response();
      assertEquals(HttpMethod.GET, req.method());
      assertEquals(DEFAULT_HTTPS_HOST_AND_PORT, req.host());
      assertEquals("/", req.path());
      assertEquals(DEFAULT_HTTPS_HOST_AND_PORT, req.getHeader(":authority"));
      assertTrue(req.isSSL());
      assertEquals("https", req.getHeader(":scheme"));
      assertEquals("/", req.getHeader(":path"));
      assertEquals("GET", req.getHeader(":method"));
      assertEquals("foo_request_value", req.getHeader("Foo_request"));
      assertEquals("bar_request_value", req.getHeader("bar_request"));
      assertEquals(2, req.headers().getAll("juu_request").size());
      assertEquals("juu_request_value_1", req.headers().getAll("juu_request").get(0));
      assertEquals("juu_request_value_2", req.headers().getAll("juu_request").get(1));
      resp.putHeader("content-type", "text/plain");
      resp.putHeader("Foo_response", "foo_response_value");
      resp.putHeader("bar_response", "bar_response_value");
      resp.putHeader("juu_response", (List<String>)Arrays.asList("juu_response_value_1", "juu_response_value_2"));
      resp.end(expected);
    });
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            assertEquals(id, streamId);
            assertEquals("200", headers.status().toString());
            assertEquals("text/plain", headers.get("content-type").toString());
            assertEquals("foo_response_value", headers.get("foo_response").toString());
            assertEquals("bar_response_value", headers.get("bar_response").toString());
            assertEquals(2, headers.getAll("juu_response").size());
            assertEquals("juu_response_value_1", headers.getAll("juu_response").get(0).toString());
            assertEquals("juu_response_value_2", headers.getAll("juu_response").get(1).toString());
            assertFalse(endStream);
          });
        }
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          String actual = data.toString(StandardCharsets.UTF_8);
          vertx.runOnContext(v -> {
            assertEquals(id, streamId);
            assertEquals(expected, actual);
            assertTrue(endOfStream);
            testComplete();
          });
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
      Http2Headers headers = GET("/").authority(DEFAULT_HTTPS_HOST_AND_PORT);
      headers.set("foo_request", "foo_request_value");
      headers.set("bar_request", "bar_request_value");
      headers.set("juu_request", "juu_request_value_1", "juu_request_value_2");
      request.encoder.writeHeaders(request.context, id, headers, 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testURI() throws Exception {
    server.requestHandler(req -> {
      assertEquals("/some/path", req.path());
      assertEquals("foo=foo_value&bar=bar_value_1&bar=bar_value_2", req.query());
      assertEquals("/some/path?foo=foo_value&bar=bar_value_1&bar=bar_value_2", req.uri());
      assertEquals("http://whatever.com/some/path?foo=foo_value&bar=bar_value_1&bar=bar_value_2", req.absoluteURI());
      assertEquals("/some/path?foo=foo_value&bar=bar_value_1&bar=bar_value_2", req.getHeader(":path"));
      assertEquals("whatever.com", req.host());
      MultiMap params = req.params();
      Set<String> names = params.names();
      assertEquals(2, names.size());
      assertTrue(names.contains("foo"));
      assertTrue(names.contains("bar"));
      assertEquals("foo_value", params.get("foo"));
      assertEquals(Collections.singletonList("foo_value"), params.getAll("foo"));
      assertEquals("bar_value_2", params.get("bar"));
      assertEquals(Arrays.asList("bar_value_1", "bar_value_2"), params.getAll("bar"));
      testComplete();
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2Headers headers = new DefaultHttp2Headers().
          method("GET").
          scheme("http").
          authority("whatever.com").
          path("/some/path?foo=foo_value&bar=bar_value_1&bar=bar_value_2");
      request.encoder.writeHeaders(request.context, id, headers, 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
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
        assertOnIOContext(ctx);
        assertFalse(resp.headWritten());
        resp.putHeader("extra", "extra-header");
      });
      resp.write("something");
      assertTrue(resp.headWritten());
      resp.end();
    });
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            assertEquals("some-header", headers.get("some").toString());
            assertEquals("extra-header", headers.get("extra").toString());
            testComplete();
          });
        }
      });
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testBodyEndHandler() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      AtomicInteger count = new AtomicInteger();
      resp.bodyEndHandler(v -> {
        assertEquals(0, count.getAndIncrement());
        assertTrue(resp.ended());
      });
      resp.write("something");
      assertEquals(0, count.get());
      resp.end();
      assertEquals(1, count.get());
      testComplete();
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testPost() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    Buffer expectedContent = TestUtils.randomBuffer(1000);
    Buffer postContent = Buffer.buffer();
    server.requestHandler(req -> {
      assertOnIOContext(ctx);
      req.handler(buff -> {
        assertOnIOContext(ctx);
        postContent.appendBuffer(buff);
      });
      req.endHandler(v -> {
        assertOnIOContext(ctx);
        req.response().putHeader("content-type", "text/plain").end("");
        assertEquals(expectedContent, postContent);
        testComplete();
      });
    });
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, POST("/").set("content-type", "text/plain"), 0, false, request.context.newPromise());
      request.encoder.writeData(request.context, id, expectedContent.getByteBuf(), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testPostFileUpload() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      Buffer tot = Buffer.buffer();
      req.setExpectMultipart(true);
      req.uploadHandler(upload -> {
        assertOnIOContext(ctx);
        assertEquals("file", upload.name());
        assertEquals("tmp-0.txt", upload.filename());
        assertEquals("image/gif", upload.contentType());
        upload.handler(tot::appendBuffer);
        upload.endHandler(v -> {
          assertEquals(tot, Buffer.buffer("some-content"));
          testComplete();
        });
      });
      req.endHandler(v -> {
        assertEquals(0, req.formAttributes().size());
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
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, POST("/form").
          set("content-type", contentType).set("content-length", contentLength), 0, false, request.context.newPromise());
      request.encoder.writeData(request.context, id, Buffer.buffer(body).getByteBuf(), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testInvalidPostFileUpload() throws Exception {
    server.requestHandler(req -> {
      req.setExpectMultipart(true);
      AtomicInteger errCount = new AtomicInteger();
      req.exceptionHandler(err -> {
        errCount.incrementAndGet();
      });
      req.endHandler(v -> {
        assertTrue(errCount.get() > 0);
        testComplete();
      });
    });
    startServer();

    String contentType = "multipart/form-data; boundary=a4e41223-a527-49b6-ac1c-315d76be757e";
    String contentLength = "225";
    String body = "--a4e41223-a527-49b6-ac1c-315d76be757e\r\n" +
        "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
        "Content-Type: image/gif; charset=ABCD\r\n" +
        "Content-Length: 12\r\n" +
        "\r\n" +
        "some-content\r\n" +
        "--a4e41223-a527-49b6-ac1c-315d76be757e--\r\n";

    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, POST("/form").
          set("content-type", contentType).set("content-length", contentLength), 0, false, request.context.newPromise());
      request.encoder.writeData(request.context, id, Buffer.buffer(body).getByteBuf(), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testConnect() throws Exception {
    server.requestHandler(req -> {
      assertEquals(HttpMethod.CONNECT, req.method());
      assertEquals("whatever.com", req.host());
      assertNull(req.path());
      assertNull(req.query());
      assertNull(req.scheme());
      assertNull(req.uri());
      assertNull(req.absoluteURI());
      testComplete();
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2Headers headers = new DefaultHttp2Headers().method("CONNECT").authority("whatever.com");
      request.encoder.writeHeaders(request.context, id, headers, 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testServerRequestPauseResume() throws Exception {
    testStreamPauseResume(req -> req);
  }

  private void testStreamPauseResume(Function<HttpServerRequest, ReadStream<Buffer>> streamProvider) throws Exception {
    Buffer expected = Buffer.buffer();
    String chunk = TestUtils.randomAlphaString(1000);
    AtomicBoolean done = new AtomicBoolean();
    AtomicBoolean paused = new AtomicBoolean();
    Buffer received = Buffer.buffer();
    server.requestHandler(req -> {
      ReadStream<Buffer> stream = streamProvider.apply(req);
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
        assertEquals(expected, received);
        testComplete();
      });
      stream.pause();
    });
    startServer();

    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
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
            request.encoder.writeData(request.context, id, buf.getByteBuf(), 0, false, request.context.newPromise());
            request.context.flush();
            request.context.invoker().executor().execute(this::send);
          } else {
            request.encoder.writeData(request.context, id, Unpooled.EMPTY_BUFFER, 0, true, request.context.newPromise());
            request.context.flush();
            paused.set(true);
          }
        }
      }
      new Anonymous().send();
    });
    fut.sync();
    await();
  }
  @Test
  public void testServerResponseWritability() throws Exception {
    testStreamWritability(req -> {
      HttpServerResponse resp = req.response();
      resp.putHeader("content-type", "text/plain");
      resp.setChunked(true);
      return resp;
    });
  }

  private void testStreamWritability(Function<HttpServerRequest, WriteStream<Buffer>> streamProvider) throws Exception {
    Context ctx = vertx.getOrCreateContext();
    String content = TestUtils.randomAlphaString(1024);
    StringBuilder expected = new StringBuilder();
    Future<Void> whenFull = Future.future();
    AtomicBoolean drain = new AtomicBoolean();
    server.requestHandler(req -> {
      WriteStream<Buffer> stream = streamProvider.apply(req);
      vertx.setPeriodic(1, timerID -> {
        if (stream.writeQueueFull()) {
          stream.drainHandler(v -> {
            assertOnIOContext(ctx);
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
    });
    startServer(ctx);

    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
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
              assertEquals(expected.toString(), received.toString());
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
      whenFull.setHandler(ar -> {
        request.context.invoker().executor().execute(() -> {
          try {
            request.decoder.flowController().consumeBytes(request.connection.stream(id), toAck.intValue());
            request.context.flush();
          } catch (Http2Exception e) {
            e.printStackTrace();
            fail(e);
          }
        });
      });
    });

    fut.sync();

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
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        int count;
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          switch (count++) {
            case 0:
              vertx.runOnContext(v -> {
                assertFalse(endStream);
              });
              break;
            case 1:
              vertx.runOnContext(v -> {
                assertEquals("foo_value", headers.get("foo").toString());
                assertEquals(1, headers.getAll("foo").size());
                assertEquals("foo_value", headers.getAll("foo").get(0).toString());
                assertEquals("bar_value", headers.getAll("bar").get(0).toString());
                assertEquals(2, headers.getAll("juu").size());
                assertEquals("juu_value_1", headers.getAll("juu").get(0).toString());
                assertEquals("juu_value_2", headers.getAll("juu").get(1).toString());
                assertTrue(endStream);
                testComplete();
              });
              break;
            default:
              vertx.runOnContext(v -> {
                fail();
              });
              break;
          }
        }
      });
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testServerResetClientStream() throws Exception {
    server.requestHandler(req -> {
      req.handler(buf -> {
        req.response().reset(8);
      });
    });
    startServer();

    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
          vertx.runOnContext(v -> {
            assertEquals(8, errorCode);
            testComplete();
          });
        }
      });
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      encoder.writeData(request.context, id, Buffer.buffer("hello").getByteBuf(), 0, false, request.context.newPromise());
    });

    fut.sync();

    await();
  }

  @Test
  public void testClientResetServerStream() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    Future<Void> bufReceived = Future.future();
    AtomicInteger resetCount = new AtomicInteger();
    server.requestHandler(req -> {
      req.handler(buf -> {
        bufReceived.complete();
      });
      req.exceptionHandler(err -> {
        assertOnIOContext(ctx);
        assertTrue(err instanceof StreamResetException);
        assertEquals(10L, ((StreamResetException) err).getCode());
        assertEquals(0, resetCount.getAndIncrement());
      });
      req.response().exceptionHandler(err -> {
        assertOnIOContext(ctx);
        assertTrue(err instanceof StreamResetException);
        assertEquals(10L, ((StreamResetException) err).getCode());
        assertEquals(1, resetCount.getAndIncrement());
      });
      req.endHandler(v -> {
        assertOnIOContext(ctx);
        assertEquals(2, resetCount.get());
        testComplete();
      });
    });
    startServer(ctx);

    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      encoder.writeData(request.context, id, Buffer.buffer("hello").getByteBuf(), 0, false, request.context.newPromise());
      bufReceived.setHandler(ar -> {
        encoder.writeRstStream(request.context, id, 10, request.context.newPromise());
        request.context.flush();
      });
    });

    fut.sync();

    await();
  }

  @Test
  public void testConnectionClose() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      HttpConnection conn = req.connection();
      conn.closeHandler(v -> {
        assertOnIOContext(ctx);
        testComplete();
      });
      req.response().putHeader("Content-Type", "text/plain").end();
    });
    startServer(ctx);

    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
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
    fut.sync();
    await();
  }

  @Test
  public void testPushPromise() throws Exception {
    testPushPromise(GET("/").authority("whatever.com"), (resp, handler ) -> {
      resp.push(HttpMethod.GET, "/wibble", handler);
    }, headers -> {
      assertEquals("GET", headers.method().toString());
      assertEquals("/wibble", headers.path().toString());
      assertEquals("whatever.com", headers.authority().toString());
    });
  }

  @Test
  public void testPushPromiseHeaders() throws Exception {
    testPushPromise(GET("/").authority("whatever.com"), (resp, handler ) -> {
      resp.push(HttpMethod.GET, "/wibble", MultiMap.caseInsensitiveMultiMap().
          set("foo", "foo_value").
          set("bar", Arrays.<CharSequence>asList("bar_value_1", "bar_value_2")), handler);
    }, headers -> {
      assertEquals("GET", headers.method().toString());
      assertEquals("/wibble", headers.path().toString());
      assertEquals(null, headers.authority());
      assertEquals("foo_value", headers.get("foo").toString());
      assertEquals(Arrays.asList("bar_value_1", "bar_value_2"), headers.getAll("bar").stream().map(CharSequence::toString).collect(Collectors.toList()));
    });
  }

  @Test
  public void testPushPromiseNoAuthority() throws Exception {
    Http2Headers get = GET("/");
    get.remove("authority");
    testPushPromise(get, (resp, handler ) -> {
      resp.push(HttpMethod.GET, "/wibble", handler);
    }, headers -> {
      assertEquals("GET", headers.method().toString());
      assertEquals("/wibble", headers.path().toString());
      assertEquals(DEFAULT_HTTPS_HOST_AND_PORT, headers.authority().toString());
    });
  }

  @Test
  public void testPushPromiseOverrideAuthority() throws Exception {
    testPushPromise(GET("/").authority("whatever.com"), (resp, handler ) -> {
      resp.push(HttpMethod.GET, "override.com", "/wibble", handler);
    }, headers -> {
      assertEquals("GET", headers.method().toString());
      assertEquals("/wibble", headers.path().toString());
      assertEquals("override.com", headers.authority().toString());
    });
  }

  @Test
  public void testPushPromiseOverrideAuthorityWithNull() throws Exception {
    testPushPromise(GET("/").authority("whatever.com"), (resp, handler ) -> {
      resp.push(HttpMethod.GET, null, "/wibble", handler);
    }, headers -> {
      assertEquals("GET", headers.method().toString());
      assertEquals("/wibble", headers.path().toString());
      assertEquals(null, headers.authority());
    });
  }

  private void testPushPromise(Http2Headers requestHeaders,
                               BiConsumer<HttpServerResponse, Handler<AsyncResult<HttpServerResponse>>> pusher,
                               Consumer<Http2Headers> headerChecker) throws Exception {
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      Handler<AsyncResult<HttpServerResponse>> handler = ar -> {
        assertSame(ctx, Vertx.currentContext());
        assertTrue(ar.succeeded());
        HttpServerResponse response = ar.result();
        response./*putHeader("content-type", "application/plain").*/end("the_content");
        assertIllegalStateException(() -> response.push(HttpMethod.GET, "/wibble2", resp -> {
        }));
      };
      pusher.accept(req.response(), handler);
    });
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
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
            assertEquals(Collections.singleton(streamId), pushed.keySet());
            assertEquals("the_content", content);
            Http2Headers pushedHeaders = pushed.get(streamId);
            headerChecker.accept(pushedHeaders);
            testComplete();
          });
          return delta;
        }
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testResetActivePushPromise() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/wibble", ar -> {
        assertTrue(ar.succeeded());
        assertOnIOContext(ctx);
        HttpServerResponse response = ar.result();
        response.exceptionHandler(err -> {
          testComplete();
        });
        response.setChunked(true).write("some_content");
      });
    });
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
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
    fut.sync();
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
        req.response().push(HttpMethod.GET, path, ar -> {
          assertTrue(ar.succeeded());
          assertSame(ctx, Vertx.currentContext());
          pushSent.add(path);
          vertx.setTimer(10, id -> {
            ar.result().end("wibble-" + val);
          });
        });
      }
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.settings.maxConcurrentStreams(3);
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
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
              assertEquals(numPushes, pushSent.size());
              assertEquals(pushReceived, pushSent);
              testComplete();
            });
          }
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testResetPendingPushPromise() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/wibble", ar -> {
        assertFalse(ar.succeeded());
        assertOnIOContext(ctx);
        testComplete();
      });
    });
    startServer(ctx);
    TestClient client = new TestClient();
    client.settings.maxConcurrentStreams(0);
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
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
    fut.sync();
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
    server.requestHandler(req -> fail());
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
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
    fut.sync();
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
          System.out.println("throwing from data");
          throw err;
        });
      });
    });
  }

  private void testHandlerFailure(boolean data, BiConsumer<RuntimeException, HttpServer> configurator) throws Exception {
    RuntimeException failure = new RuntimeException();
    io.vertx.core.http.Http2Settings settings = TestUtils.randomHttp2Settings();
    server.close();
    server = vertx.createHttpServer(serverOptions.setInitialSettings(settings));
    configurator.accept(failure, server);
    Context ctx = vertx.getOrCreateContext();
    ctx.exceptionHandler(err -> {
      assertSame(err, failure);
      testComplete();
    });
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, !data, request.context.newPromise());
      if (data) {
        request.encoder.writeData(request.context, id, Buffer.buffer("hello").getByteBuf(), 0, true, request.context.newPromise());
      }
    });
    fut.sync();
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

  private void testSendFile(Buffer expected, String path, long offset, long length) throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.bodyEndHandler(v -> {
        assertEquals(resp.bytesWritten(), length);
        complete();
      });
      resp.sendFile(path, offset, length);
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        Buffer buffer = Buffer.buffer();
        Http2Headers responseHeaders;
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          responseHeaders = headers;
        }
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          buffer.appendBuffer(Buffer.buffer(data.duplicate()));
          if (endOfStream) {
            vertx.runOnContext(v -> {
              assertEquals("" + length, responseHeaders.get("content-length").toString());
              assertEquals(expected, buffer);
              complete();
            });
          }
          return data.readableBytes() + padding;
        }
      });
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testStreamError() throws Exception {
    waitFor(4);
    Future<Void> when = Future.future();
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      req.exceptionHandler(err -> {
        // Called twice : reset + close
        assertOnIOContext(ctx);
        complete();
      });
      req.response().exceptionHandler(err -> {
        // Called twice : reset + close
        assertOnIOContext(ctx);
        complete();
      });
      when.complete();
    });
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      request.context.flush();
      when.setHandler(ar -> {
        // Send a corrupted frame on purpose to check we get the corresponding error in the request exception handler
        // the error is : greater padding value 0c -> 1F
        // ChannelFuture a = encoder.frameWriter().writeData(request.context, id, Buffer.buffer("hello").getByteBuf(), 12, false, request.context.newPromise());
        // normal frame    : 00 00 12 00 08 00 00 00 03 0c 68 65 6c 6c 6f 00 00 00 00 00 00 00 00 00 00 00 00
        // corrupted frame : 00 00 12 00 08 00 00 00 03 1F 68 65 6c 6c 6f 00 00 00 00 00 00 00 00 00 00 00 00
        request.channel.write(Buffer.buffer(new byte[]{
            0x00, 0x00, 0x12, 0x00, 0x08, 0x00, 0x00, 0x00, (byte)(id & 0xFF), 0x1F, 0x68, 0x65, 0x6c, 0x6c,
            0x6f, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        }).getByteBuf());
        request.context.flush();
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testPromiseStreamError() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    waitFor(2);
    Future<Void> when = Future.future();
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/wibble", ar -> {
        assertTrue(ar.succeeded());
        assertOnIOContext(ctx);
        when.complete();
        HttpServerResponse resp = ar.result();
        resp.exceptionHandler(err -> {
          assertSame(ctx, Vertx.currentContext());
          complete();
        });
        resp.setChunked(true).write("whatever"); // Transition to half-closed remote
      });
    });
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
          when.setHandler(ar -> {
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
    fut.sync();
    await();
  }

  @Test
  public void testConnectionDecodeError() throws Exception {
    Context ctx = vertx.getOrCreateContext();
    waitFor(5);
    Future<Void> when = Future.future();
    server.requestHandler(req -> {
      req.exceptionHandler(err -> {
        // Called twice : reset + close
        assertSame(ctx, Vertx.currentContext());
        complete();
      });
      req.response().exceptionHandler(err -> {
        // Called twice : reset + close
        assertSame(ctx, Vertx.currentContext());
        complete();
      });
      req.connection().exceptionHandler(err -> {
        assertSame(ctx, Vertx.currentContext());
        complete();
      });
      when.complete();
    });
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      when.setHandler(ar -> {
        encoder.frameWriter().writeRstStream(request.context, 10, 0, request.context.newPromise());
        request.context.flush();
      });
      encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
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
          assertTrue(done.get());
        });
        req.response().exceptionHandler(err -> {
          assertTrue(done.get());
        });
      } else {
        assertEquals(0, status.getAndIncrement());
        req.exceptionHandler(err -> {
          closed.incrementAndGet();
        });
        req.response().exceptionHandler(err -> {
          closed.incrementAndGet();
        });
        HttpConnection conn = req.connection();
        conn.shutdownHandler(v -> {
          assertTrue(done.get());
        });
        conn.closeHandler(v -> {
          assertTrue(done.get());
        });
        ctx.runOnContext(v1 -> {
          conn.goAway(0, first.get().response().streamId());
          vertx.setTimer(300, timerID -> {
            assertEquals(1, status.getAndIncrement());
            done.set(true);
            complete();
          });
        });
      }
    };
    testServerSendGoAway(requestHandler, 0);
  }

  @Test
  public void testServerSendGoAwayInteralError() throws Exception {
    waitFor(3);
    AtomicReference<HttpServerRequest> first = new AtomicReference<>();
    AtomicInteger status = new AtomicInteger();
    AtomicInteger closed = new AtomicInteger();
    Handler<HttpServerRequest> requestHandler = req -> {
      if (first.compareAndSet(null, req)) {
        req.exceptionHandler(err -> {
          fail();
        });
        req.response().exceptionHandler(err -> {
          closed.incrementAndGet();
        });
      } else {
        assertEquals(0, status.getAndIncrement());
        req.exceptionHandler(err -> {
          closed.incrementAndGet();
        });
        req.response().exceptionHandler(err -> {
          closed.incrementAndGet();
        });
        HttpConnection conn = req.connection();
        conn.closeHandler(v -> {
          assertEquals(3, closed.get());
          assertEquals(1, status.get());
          complete();
        });
        conn.shutdownHandler(v -> {
          assertEquals(1, status.get());
          complete();
        });
        conn.goAway(2, first.get().response().streamId());
      }
    };
    testServerSendGoAway(requestHandler, 2);
  }

  @Test
  public void testShutdownWithTimeout() throws Exception {
    waitFor(2);
    AtomicInteger closed = new AtomicInteger();
    AtomicReference<HttpServerRequest> first = new AtomicReference<>();
    AtomicInteger status = new AtomicInteger();
    Handler<HttpServerRequest> requestHandler = req -> {
      if (first.compareAndSet(null, req)) {
        req.exceptionHandler(err -> {
          fail();
        });
        req.response().exceptionHandler(err -> {
          closed.incrementAndGet();
        });
      } else {
        assertEquals(0, status.getAndIncrement());
        req.exceptionHandler(err -> {
          fail();
        });
        req.response().exceptionHandler(err -> {
          closed.incrementAndGet();
        });
        HttpConnection conn = req.connection();
        conn.closeHandler(v -> {
          assertEquals(2, closed.get());
          assertEquals(1, status.getAndIncrement());
          complete();
        });
        conn.shutdown(300);
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
          fail();
        });
        req.response().exceptionHandler(err -> {
          fail();
        });
      } else {
        assertEquals(0, status.getAndIncrement());
        req.exceptionHandler(err -> {
          fail();
        });
        req.response().exceptionHandler(err -> {
          fail();
        });
        HttpConnection conn = req.connection();
        conn.closeHandler(v -> {
          assertEquals(2, status.getAndIncrement());
          complete();
        });
        conn.shutdown();
        vertx.setTimer(300, timerID -> {
          assertEquals(1, status.getAndIncrement());
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
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {
          vertx.runOnContext(v -> {
            assertEquals(expectedError, errorCode);
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
    fut.sync();
    await();
  }

  @Test
  public void testServerClose() throws Exception {
    waitFor(2);
    AtomicInteger status = new AtomicInteger();
    Handler<HttpServerRequest> requestHandler = req -> {
      HttpConnection conn = req.connection();
      conn.shutdownHandler(v -> {
        assertEquals(0, status.getAndIncrement());
      });
      conn.closeHandler(v -> {
        assertEquals(1, status.getAndIncrement());
        complete();
      });
      conn.close();
    };
    server.requestHandler(requestHandler);
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.channel.closeFuture().addListener(v1 -> {
        vertx.runOnContext(v2 -> {
          complete();
        });
      });
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {
          vertx.runOnContext(v -> {
            assertEquals(0, errorCode);
          });
        }
      });
      Http2ConnectionEncoder encoder = request.encoder;
      int id = request.nextStreamId();
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();

    });
    fut.sync();
    await();
  }

  @Test
  public void testClientSendGoAwayNoError() throws Exception {
    Future<Void> abc = Future.future();
    Context ctx = vertx.getOrCreateContext();
    Handler<HttpServerRequest> requestHandler = req -> {
      HttpConnection conn = req.connection();
      AtomicInteger numShutdown = new AtomicInteger();
      AtomicBoolean completed = new AtomicBoolean();
      conn.shutdownHandler(v -> {
        assertOnIOContext(ctx);
        numShutdown.getAndIncrement();
        vertx.setTimer(100, timerID -> {
          // Delay so we can check the connection is not closed
          completed.set(true);
          testComplete();
        });
      });
      conn.goAwayHandler(ga -> {
        assertOnIOContext(ctx);
        assertEquals(0, numShutdown.get());
        req.response().end();
      });
      conn.closeHandler(v -> {
        assertTrue(completed.get());
      });
      abc.complete();
    };
    server.requestHandler(requestHandler);
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      Http2ConnectionEncoder encoder = request.encoder;
      int id = request.nextStreamId();
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
      abc.setHandler(ar -> {
        encoder.writeGoAway(request.context, id, 0, Unpooled.EMPTY_BUFFER, request.context.newPromise());
        request.context.flush();
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testClientSendGoAwayInternalError() throws Exception {
    Future<Void> abc = Future.future();
    Context ctx = vertx.getOrCreateContext();
    Handler<HttpServerRequest> requestHandler = req -> {
      HttpConnection conn = req.connection();
      AtomicInteger status = new AtomicInteger();
      conn.goAwayHandler(ga -> {
        assertOnIOContext(ctx);
        assertEquals(0, status.getAndIncrement());
        req.response().end();
      });
      conn.shutdownHandler(v -> {
        assertOnIOContext(ctx);
        assertEquals(1, status.getAndIncrement());
      });
      conn.closeHandler(v -> {
        assertEquals(2, status.getAndIncrement());
        testComplete();
      });
      abc.complete();
    };
    server.requestHandler(requestHandler);
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      Http2ConnectionEncoder encoder = request.encoder;
      int id = request.nextStreamId();
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
      abc.setHandler(ar -> {
        encoder.writeGoAway(request.context, id, 3, Unpooled.EMPTY_BUFFER, request.context.newPromise());
        request.context.flush();
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testShutdownOverride() throws Exception {
    AtomicLong shutdown = new AtomicLong();
    Handler<HttpServerRequest> requestHandler = req -> {
      HttpConnection conn = req.connection();
      shutdown.set(System.currentTimeMillis());
      conn.shutdown(10000);
      vertx.setTimer(300, v -> {
        conn.shutdown(300);
      });
    };
    server.requestHandler(requestHandler);
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.channel.closeFuture().addListener(v1 -> {
        vertx.runOnContext(v2 -> {
          assertTrue(shutdown.get() - System.currentTimeMillis() < 1200);
          testComplete();
        });
      });
      Http2ConnectionEncoder encoder = request.encoder;
      int id = request.nextStreamId();
      encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
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
      assertTrue(resp.headWritten());
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
      assertIllegalStateException(() -> resp.reset(0));
      assertIllegalStateException(() -> resp.closeHandler(v -> {}));
      assertIllegalStateException(() -> resp.drainHandler(v -> {}));
      assertIllegalStateException(() -> resp.exceptionHandler(err -> {}));
      assertIllegalStateException(resp::writeQueueFull);
      assertIllegalStateException(() -> resp.setWriteQueueMaxSize(100));
      assertIllegalStateException(() -> resp.putTrailer("a", "b"));
      assertIllegalStateException(() -> resp.putTrailer("a", (CharSequence) "b"));
      assertIllegalStateException(() -> resp.putTrailer("a", (Iterable<String>)Arrays.asList("a", "b")));
      assertIllegalStateException(() -> resp.putTrailer("a", (Arrays.<CharSequence>asList("a", "b"))));
      assertIllegalStateException(() -> resp.push(HttpMethod.GET, "/whatever", ar -> {}));
      complete();
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
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
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            assertEquals(null, headers.get(HttpHeaderNames.CONTENT_ENCODING));
            complete();
          });
        }
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          String s = data.toString(StandardCharsets.UTF_8);
          vertx.runOnContext(v -> {
            assertEquals(expected, s);
            complete();
          });
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/").add("accept-encoding", "gzip"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testResponseCompressionEnabled() throws Exception {
    waitFor(2);
    String expected = TestUtils.randomAlphaString(1000);
    server.close();
    server = vertx.createHttpServer(serverOptions.setCompressionSupported(true));
    server.requestHandler(req -> {
      req.response().end(expected);
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            assertEquals("gzip", headers.get(HttpHeaderNames.CONTENT_ENCODING).toString());
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
                baos.write(i);;
              }
              decoded = baos.toString();
            } catch (IOException e) {
              fail(e);
              return;
            }
            assertEquals(expected, decoded);
            complete();
          });
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
      int id = request.nextStreamId();
      request.encoder.writeHeaders(request.context, id, GET("/").add("accept-encoding", "gzip"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void test100ContinueHandledManually() throws Exception {
    server.requestHandler(req -> {
      assertEquals("100-continue", req.getHeader("expect"));
      HttpServerResponse resp = req.response();
      resp.writeContinue();
      req.bodyHandler(body -> {
        assertEquals("the-body", body.toString());
        resp.putHeader("wibble", "wibble-value").end();
      });
    });
    test100Continue();
  }

  @Test
  public void test100ContinueHandledAutomatically() throws Exception {
    server.close();
    server = vertx.createHttpServer(serverOptions.setHandle100ContinueAutomatically(true));
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      req.bodyHandler(body -> {
        assertEquals("the-body", body.toString());
        resp.putHeader("wibble", "wibble-value").end();
      });
    });
    test100Continue();
  }

  private void test100Continue() throws Exception {
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        int count = 0;
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          switch (count++) {
            case 0:
              vertx.runOnContext(v -> {
                assertEquals("100", headers.status().toString());
              });
              request.encoder.writeData(request.context, id, Buffer.buffer("the-body").getByteBuf(), 0, true, request.context.newPromise());
              request.context.flush();
              break;
            case 1:
              vertx.runOnContext(v -> {
                assertEquals("200", headers.status().toString());
                assertEquals("wibble-value", headers.get("wibble").toString());
                testComplete();
              });
              break;
            default:
              vertx.runOnContext(v -> {
                fail();
              });
          }
        }
      });
      request.encoder.writeHeaders(request.context, id, GET("/").add("expect", "100-continue"), 0, false, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void test100ContinueRejectedManually() throws Exception {
    server.requestHandler(req -> {
      req.response().setStatusCode(405).end();
      req.handler(buf -> {
        fail();
      });
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        int count = 0;
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          switch (count++) {
            case 0:
              vertx.runOnContext(v -> {
                assertEquals("405", headers.status().toString());
                vertx.setTimer(100, v2 -> {
                  testComplete();
                });
              });
              break;
            default:
              vertx.runOnContext(v -> {
                fail();
              });
          }
        }
      });
      request.encoder.writeHeaders(request.context, id, GET("/").add("expect", "100-continue"), 0, false, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
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
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            assertEquals("200", headers.status().toString());
            assertFalse(endStream);
          });
          request.encoder.writeData(request.context, id, Buffer.buffer("some-data").getByteBuf(), 0, false, request.context.newPromise());
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
              assertFalse(endOfStream);
            });
            request.encoder.writeData(request.context, id, Buffer.buffer("last-data").getByteBuf(), 0, true, request.context.newPromise());
          } else if (endOfStream) {
            vertx.runOnContext(v -> {
              assertEquals("last-data", received.toString());
              complete();
            });
          }
          return data.readableBytes() + padding;
        }
      });
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
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
      NetSocket socket = req.netSocket();
      socket.sendFile(path, offset, length, ar -> {
        assertTrue(ar.succeeded());
        socket.end();
      });
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          vertx.runOnContext(v -> {
            assertEquals("200", headers.status().toString());
            assertFalse(endStream);
          });
        }
        Buffer received = Buffer.buffer();
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          received.appendBuffer(Buffer.buffer(data.copy()));
          if (endOfStream) {
            vertx.runOnContext(v -> {
              assertEquals(received, expected);
              testComplete();
            });
          }
          return data.readableBytes() + padding;
        }
      });
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, true, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
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
            socket.write(buff);
            socket.close();
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
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        int count = 0;
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          int c = count++;
          vertx.runOnContext(v -> {
            assertEquals(0, c);
          });
          request.encoder.writeData(request.context, id, Buffer.buffer("some-data").getByteBuf(), 0, false, request.context.newPromise());
          request.context.flush();
        }
        StringBuilder received = new StringBuilder();
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          String s = data.toString(StandardCharsets.UTF_8);
          received.append(s);
          if (endOfStream) {
            request.encoder.writeData(request.context, id, Buffer.buffer("last-data").getByteBuf(), 0, true, request.context.newPromise());
            vertx.runOnContext(v -> {
              assertEquals("some-data", received.toString());
              complete();
            });
          }
          return data.readableBytes() + padding;
        }
      });
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testNetSocketHandleReset() throws Exception {
    server.requestHandler(req -> {
      NetSocket socket = req.netSocket();
      AtomicInteger status = new AtomicInteger();
      socket.exceptionHandler(err -> {
        assertTrue(err instanceof StreamResetException);
        StreamResetException ex = (StreamResetException) err;
        assertEquals(0, ex.getCode());
        assertEquals(0, status.getAndIncrement());
      });
      socket.endHandler(v -> {
        fail();
      });
      socket.closeHandler(v  -> {
        assertEquals(1, status.getAndIncrement());
        testComplete();
      });
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        int count = 0;
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          int c = count++;
          vertx.runOnContext(v -> {
            assertEquals(0, c);
          });
          request.encoder.writeRstStream(ctx, streamId, 0, ctx.newPromise());
          request.context.flush();
        }
      });
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testNetSocketPauseResume() throws Exception {
    testStreamPauseResume(HttpServerRequest::netSocket);
  }

  @Test
  public void testNetSocketWritability() throws Exception {
    testStreamWritability(HttpServerRequest::netSocket);
  }

  @Test
  public void testUnknownFrame() throws Exception {
    Buffer expectedSend = TestUtils.randomBuffer(500);
    Buffer expectedRecv = TestUtils.randomBuffer(500);
    Context ctx = vertx.getOrCreateContext();
    server.requestHandler(req -> {
      req.unknownFrameHandler(frame -> {
        assertOnIOContext(ctx);
        assertEquals(10, frame.type());
        assertEquals(253, frame.flags());
        assertEquals(expectedSend, frame.payload());
        req.response().writeFrame(12, 134, expectedRecv).end();
      });
    });
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
        int status = 0;
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
          int s = status++;
          vertx.runOnContext(v -> {
            assertEquals(0, s);
          });
        }
        @Override
        public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf payload) {
          int s = status++;
          Buffer recv = Buffer.buffer(payload.copy());
          vertx.runOnContext(v -> {
            assertEquals(1, s);
            assertEquals(12, frameType);
            assertEquals(134, flags.value());
            assertEquals(expectedRecv, recv);
          });
        }
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          int len = data.readableBytes();
          int s = status++;
          vertx.runOnContext(v -> {
            assertEquals(2, s);
            assertEquals(0, len);
            assertTrue(endOfStream);
            testComplete();
          });
          return data.readableBytes() + padding;
        }
      });
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      request.encoder.writeFrame(request.context, (byte)10, id, new Http2Flags((short) 253), expectedSend.getByteBuf(), request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    await();
  }

  @Test
  public void testUpgradeToClearText() throws Exception {
    server.close();
    server = vertx.createHttpServer(serverOptions.
        setHost(DEFAULT_HTTP_HOST).
        setPort(DEFAULT_HTTP_PORT).
        setUseAlpn(false).
        setSsl(false).
        setInitialSettings(new io.vertx.core.http.Http2Settings().setMaxConcurrentStreams(20000)));
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_2, req.version());
      assertEquals(10000, req.connection().remoteSettings().getMaxConcurrentStreams());
      assertFalse(req.isSSL());
      req.response().end();
    });
    startServer();
    client = vertx.createHttpClient(clientOptions.
        setUseAlpn(false).
        setInitialSettings(new io.vertx.core.http.Http2Settings().setMaxConcurrentStreams(10000)));
    HttpClientRequest req = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath");
    req.handler(resp -> {
      assertEquals(HttpVersion.HTTP_2, resp.version());
      assertEquals(20000, req.connection().remoteSettings().getMaxConcurrentStreams());
      testComplete();
    }).exceptionHandler(this::fail).end();
    await();
  }

  @Test
  public void testUpgradeToClearTextInvalidConnectionHeader() throws Exception {
    testUpgradeFailure(vertx.getOrCreateContext(), client -> client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
        .putHeader("Upgrade", "h2c")
        .putHeader("Connection", "Upgrade")
        .putHeader("HTTP2-Settings", ""));
  }

  @Test
  public void testUpgradeToClearTextMalformedSettings() throws Exception {
    testUpgradeFailure(vertx.getOrCreateContext(), client -> client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
        .putHeader("Upgrade", "h2c")
        .putHeader("Connection", "Upgrade,HTTP2-Settings")
        .putHeader("HTTP2-Settings", "incorrect-settings"));
  }

  @Test
  public void testUpgradeToClearTextInvalidSettings() throws Exception {
    Buffer buffer = Buffer.buffer();
    buffer.appendUnsignedShort(5).appendUnsignedInt((0xFFFFFF + 1));
    String s = new String(Base64.getUrlEncoder().encode(buffer.getBytes()), StandardCharsets.UTF_8);
    testUpgradeFailure(vertx.getOrCreateContext(), client -> client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
        .putHeader("Upgrade", "h2c")
        .putHeader("Connection", "Upgrade,HTTP2-Settings")
        .putHeader("HTTP2-Settings", s));
  }

  @Test
  public void testUpgradeToClearTextMissingSettings() throws Exception {
    testUpgradeFailure(vertx.getOrCreateContext(), client -> client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
        .putHeader("Upgrade", "h2c")
        .putHeader("Connection", "Upgrade,HTTP2-Settings"));
  }

  @Test
  public void testUpgradeToClearTextWorkerContext() throws Exception {
    testUpgradeFailure(createWorker(), client -> client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
        .putHeader("Upgrade", "h2c")
        .putHeader("Connection", "Upgrade,HTTP2-Settings")
        .putHeader("HTTP2-Settings", ""));
  }

  private void testUpgradeFailure(Context context, Function<HttpClient, HttpClientRequest> doRequest) throws Exception {
    server.close();
    server = vertx.createHttpServer(serverOptions.setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT).setUseAlpn(false).setSsl(false));
    server.requestHandler(req -> {
      fail();
    });
    startServer(context);
    client = vertx.createHttpClient(clientOptions.setProtocolVersion(HttpVersion.HTTP_1_1).setUseAlpn(false));
    doRequest.apply(client).handler(resp -> {
      assertEquals(400, resp.statusCode());
      assertEquals(HttpVersion.HTTP_1_1, resp.version());
      testComplete();
    }).exceptionHandler(this::fail).end();
    await();
  }

  @Test
  public void testIdleTimeout() throws Exception {
    waitFor(5);
    server.close();
    server = vertx.createHttpServer(serverOptions.setIdleTimeout(2));
    server.requestHandler(req -> {
      req.exceptionHandler(err -> {
        assertTrue(err instanceof ClosedChannelException);
        complete();
      });
      req.response().exceptionHandler(err -> {
        assertTrue(err instanceof ClosedChannelException);
        complete();
      });
      req.response().closeHandler(v -> {
        complete();
      });
      req.connection().closeHandler(v -> {
        complete();
      });
    });
    startServer();
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      int id = request.nextStreamId();
      request.decoder.frameListener(new Http2EventAdapter() {
      });
      request.encoder.writeHeaders(request.context, id, GET("/"), 0, false, request.context.newPromise());
      request.context.flush();
    });
    fut.sync();
    fut.channel().closeFuture().addListener(v1 -> {
      vertx.runOnContext(v2 -> {
        complete();
      });
    });
    await();
  }

  @Test
  public void testFallbackOnHttp1ForWorkerContext() throws Exception {
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      req.response().end();
    });
    startServer(createWorker());
    client = vertx.createHttpClient(clientOptions);
    client.get(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/somepath", resp -> {
      assertEquals(HttpVersion.HTTP_1_1, resp.version());
      testComplete();
    }).end();
    await();
  }

  @Test
  public void testSendPing() throws Exception {
    waitFor(2);
    Buffer expected = TestUtils.randomBuffer(8);
    Context ctx = vertx.getOrCreateContext();
    server.close();
    server.connectionHandler(conn -> {
      conn.ping(expected, ar -> {
        assertSame(ctx, Vertx.currentContext());
        assertTrue(ar.succeeded());
        assertEquals(expected, ar.result());
        complete();
      });
    });
    server.requestHandler(req -> fail());
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.decoder.frameListener(new Http2EventAdapter() {
        @Override
        public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
          Buffer buffer = Buffer.buffer(data.copy());
          vertx.runOnContext(v -> {
            assertEquals(expected, buffer);
            complete();
          });
        }
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testReceivePing() throws Exception {
    Buffer expected = TestUtils.randomBuffer(8);
    Context ctx = vertx.getOrCreateContext();
    server.close();
    server.connectionHandler(conn -> {
      conn.pingHandler(buff -> {
        assertOnIOContext(ctx);
        assertEquals(expected, buff);
        testComplete();
      });
    });
    server.requestHandler(req -> fail());
    startServer(ctx);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, request -> {
      request.encoder.writePing(request.context, false, expected.getByteBuf(), request.context.newPromise());
    });
    fut.sync();
    await();
  }
}
