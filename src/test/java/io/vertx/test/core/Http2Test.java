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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.VertxHttp2Handler;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.core.net.impl.KeyStoreHelper;
import io.vertx.core.net.impl.SSLHelper;
import okhttp3.CertificatePinner;
import okhttp3.ConnectionSpec;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2Test extends HttpTestBase {

  private HttpServerOptions serverOptions;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    serverOptions = new HttpServerOptions()
        .setPort(4043)
        .setHost("localhost")
        .setUseAlpn(true)
        .setSsl(true)
        .addEnabledCipherSuite("TLS_RSA_WITH_AES_128_CBC_SHA") // Non Diffie-helman -> debuggable in wireshark
        .setKeyStoreOptions((JksOptions) getServerCertOptions(KeyCert.JKS));

    server = vertx.createHttpServer(serverOptions);

  }

  private SSLContext createSSLContext() throws Exception {
    KeyStoreHelper helper = KeyStoreHelper.create((VertxInternal) vertx, (TrustOptions) getServerCertOptions(KeyCert.JKS));
    TrustManager[] trustMgrs = helper.getTrustMgrs((VertxInternal) vertx);
    SSLContext context = SSLContext.getInstance("SSL");
    context.init(null, trustMgrs, new java.security.SecureRandom());
    return context;
  }

  private OkHttpClient createHttp2Client() throws Exception {
    return createHttp2ClientBuilder().build();
  }

  private OkHttpClient.Builder createHttp2ClientBuilder() throws Exception {
    CertificatePinner certificatePinner = new CertificatePinner.Builder()
        .add("localhost", "sha1/c9qKvZ9pYojzJD4YQRfuAd0cHVA=")
        .build();

    SSLContext sc = createSSLContext();

    return new OkHttpClient.Builder()
        .readTimeout(100, TimeUnit.SECONDS)
        .writeTimeout(100, TimeUnit.SECONDS)
        .sslSocketFactory(sc.getSocketFactory()).hostnameVerifier((hostname, session) -> true)
        .protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .certificatePinner(certificatePinner)
        .connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS));
  }

  class TestClient {

    public final Http2Settings settings = new Http2Settings();

    public class Request {
      public final ChannelHandlerContext context;
      public final Http2Connection connection;
      public final Http2ConnectionEncoder encoder;
      public final Http2ConnectionDecoder decoder;

      public Request(ChannelHandlerContext context, Http2Connection connection, Http2ConnectionEncoder encoder, Http2ConnectionDecoder decoder) {
        this.context = context;
        this.connection = connection;
        this.encoder = encoder;
        this.decoder = decoder;
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
          SSLHelper sslHelper = new SSLHelper(new HttpClientOptions().setUseAlpn(true), null, KeyStoreHelper.create((VertxInternal) vertx, getClientTrustOptions(Trust.JKS)));
          SslHandler sslHandler = sslHelper.createSslHandler((VertxInternal) vertx, true, host, port);
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
                Request request = new Request(ctx, connection, clientHandler.encoder(), clientHandler.decoder());
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
  public void testServerInitialSettings() throws Exception {
    Http2Settings settings = randomSettings();
    CountDownLatch latch = new CountDownLatch(1);
    server.close();
    server = vertx.createHttpServer(serverOptions.setHttp2Settings(VertxHttp2Handler.toVertxSettings(settings)));
    server.requestHandler(req -> fail()).listen(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);
    TestClient client = new TestClient();
    client.settings.maxConcurrentStreams(0);
    ChannelFuture fut = client.connect(4043, "localhost", request -> {
      request.decoder.frameListener(new Http2FrameAdapter() {
        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings newSettings) throws Http2Exception {
          vertx.runOnContext(v -> {
            assertEquals(settings.headerTableSize(), newSettings.headerTableSize());
            assertEquals(settings.maxConcurrentStreams(), newSettings.maxConcurrentStreams());
            assertEquals(settings.initialWindowSize(), newSettings.initialWindowSize());
            assertEquals(settings.maxFrameSize(), newSettings.maxFrameSize());
            assertEquals(settings.maxHeaderListSize(), newSettings.maxHeaderListSize());
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
    Http2Settings expectedSettings = randomSettings();
    CountDownLatch latch = new CountDownLatch(1);
    server.close();
    server = vertx.createHttpServer(serverOptions);
    server.requestHandler(req -> {
      req.connection().updateSettings(VertxHttp2Handler.toVertxSettings(expectedSettings), ar -> {
        io.vertx.core.http.Http2Settings ackedSettings = req.connection().settings();
        assertEquals(expectedSettings.maxHeaderListSize(), ackedSettings.getMaxHeaderListSize());
        assertEquals(expectedSettings.maxFrameSize(), ackedSettings.getMaxFrameSize());
        assertEquals(expectedSettings.initialWindowSize(), ackedSettings.getInitialWindowSize());
        assertEquals(expectedSettings.maxConcurrentStreams(), ackedSettings.getMaxConcurrentStreams());
        assertEquals((long) expectedSettings.headerTableSize(), (long) ackedSettings.getHeaderTableSize());
        complete();
      });
    }).listen(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);
    TestClient client = new TestClient();
    client.settings.maxConcurrentStreams(0);
    ChannelFuture fut = client.connect(4043, "localhost", request -> {
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
                assertEquals(expectedSettings.maxHeaderListSize(), newSettings.maxHeaderListSize());
                assertEquals(expectedSettings.maxFrameSize(), newSettings.maxFrameSize());
                assertEquals(expectedSettings.initialWindowSize(), newSettings.initialWindowSize());
                assertEquals(expectedSettings.maxConcurrentStreams(), newSettings.maxConcurrentStreams());
                assertEquals((long) expectedSettings.headerTableSize(), (long) newSettings.headerTableSize());
                complete();
                break;
              default:
                fail();
            }
          });
        }
      });
      int id = request.connection.local().nextStreamId();
      request.encoder.writeHeaders(request.context, id, new DefaultHttp2Headers(), 0, false, request.context.newPromise());
    });
    fut.sync();
    await();
  }

  @Test
  public void testClientSettings() throws Exception {
    Http2Settings initialSettings = randomSettings();
    Http2Settings updatedSettings = randomSettings();
    CountDownLatch latch = new CountDownLatch(1);
    CompletableFuture<Void> settingsRead = new CompletableFuture<>();
    server.requestHandler(req -> {
      io.vertx.core.http.Http2Settings settings = req.connection().clientSettings();
      assertEquals(initialSettings.maxHeaderListSize(), settings.getMaxHeaderListSize());
      assertEquals(initialSettings.maxFrameSize(), settings.getMaxFrameSize());
      assertEquals(initialSettings.initialWindowSize(), settings.getInitialWindowSize());
      assertEquals(initialSettings.maxConcurrentStreams(), settings.getMaxConcurrentStreams());
      assertEquals((long) initialSettings.headerTableSize(), (long) settings.getHeaderTableSize());
      req.connection().clientSettingsHandler(update -> {
        assertEquals(updatedSettings.maxHeaderListSize(), update.getMaxHeaderListSize());
        assertEquals(updatedSettings.maxFrameSize(), update.getMaxFrameSize());
        assertEquals(updatedSettings.initialWindowSize(), update.getInitialWindowSize());
        assertEquals(updatedSettings.maxConcurrentStreams(), update.getMaxConcurrentStreams());
        assertEquals((long) updatedSettings.headerTableSize(), (long) update.getHeaderTableSize());
        testComplete();
      });
      settingsRead.complete(null);
    }).listen(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);
    TestClient client = new TestClient();
    client.settings.putAll(initialSettings);
    ChannelFuture fut = client.connect(4043, "localhost", request -> {
      int id = request.connection.local().nextStreamId();
      request.encoder.writeHeaders(request.context, id, new DefaultHttp2Headers(), 0, false, request.context.newPromise());
      request.context.flush();
      settingsRead.thenAccept(v -> {
        request.encoder.writeSettings(request.context, updatedSettings, request.context.newPromise());
        request.context.flush();
      });
    });
    fut.sync();
    await();
  }

  private Http2Settings randomSettings() {
    int headerTableSize = 10 + TestUtils.randomPositiveInt() % (Http2CodecUtil.MAX_HEADER_TABLE_SIZE - 10);
    boolean enablePush = TestUtils.randomBoolean();
    long maxConcurrentStreams = TestUtils.randomPositiveLong() % (Http2CodecUtil.MAX_CONCURRENT_STREAMS - 10);
    int initialWindowSize = 10 + TestUtils.randomPositiveInt() % (Http2CodecUtil.MAX_INITIAL_WINDOW_SIZE - 10);
    int maxFrameSize = Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND + TestUtils.randomPositiveInt() % (Http2CodecUtil.MAX_FRAME_SIZE_UPPER_BOUND - Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND);
    int maxHeaderListSize = 10 + TestUtils.randomPositiveInt() % (int) (Http2CodecUtil.MAX_HEADER_LIST_SIZE - 10);
    Http2Settings settings = new Http2Settings();
    settings.headerTableSize(headerTableSize);
    settings.pushEnabled(enablePush);
    settings.maxConcurrentStreams(maxConcurrentStreams);
    settings.initialWindowSize(initialWindowSize);
    settings.maxFrameSize(maxFrameSize);
    settings.maxHeaderListSize(maxHeaderListSize);
    return settings;
  }

  @Test
    public void testGet() throws Exception {
    String content = TestUtils.randomAlphaString(1000);
    AtomicBoolean requestEnded = new AtomicBoolean();
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      req.endHandler(v -> {
        requestEnded.set(true);
      });
      HttpServerResponse resp = req.response();
      resp.putHeader("content-type", "text/plain");
      resp.putHeader("Foo", "foo_value");
      resp.putHeader("bar", "bar_value");
      resp.putHeader("juu", (List<String>)Arrays.asList("juu_value_1", "juu_value_2"));
      resp.end(content);
    })
        .listen(ar -> {
          assertTrue(ar.succeeded());
          latch.countDown();
        });
    awaitLatch(latch);
    OkHttpClient client = createHttp2Client();
    Request request = new Request.Builder().url("https://localhost:4043/").build();
    Response response = client.newCall(request).execute();
    assertEquals(Protocol.HTTP_2, response.protocol());
    assertEquals(content, response.body().string());
    assertEquals("text/plain", response.header("content-type"));
    System.out.println(response.headers().names());
    assertEquals("foo_value", response.header("foo"));
    assertEquals("bar_value", response.header("bar"));
    assertEquals(Arrays.asList("juu_value_1", "juu_value_2"), response.headers("juu"));
  }

  @Test
  public void testURI() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      assertEquals("/some/path", req.path());
      assertEquals("foo=foo_value&bar=bar_value_1&bar=bar_value_2", req.query());
      assertEquals("/some/path?foo=foo_value&bar=bar_value_1&bar=bar_value_2", req.uri());
      assertEquals("https://localhost:4043/some/path?foo=foo_value&bar=bar_value_1&bar=bar_value_2", req.absoluteURI());
      MultiMap params = req.params();
      Set<String> names = params.names();
      assertEquals(2, names.size());
      assertTrue(names.contains("foo"));
      assertTrue(names.contains("bar"));
      assertEquals("foo_value", params.get("foo"));
      assertEquals(Collections.singletonList("foo_value"), params.getAll("foo"));
      assertEquals("bar_value_2", params.get("bar"));
      assertEquals(Arrays.asList("bar_value_1", "bar_value_2"), params.getAll("bar"));
      req.response().putHeader("content-type", "text/plain").end("done");
    })
        .listen(ar -> {
          assertTrue(ar.succeeded());
          latch.countDown();
        });
    awaitLatch(latch);
    OkHttpClient client = createHttp2Client();
    Request request = new Request.Builder().url("https://localhost:4043/some/path?foo=foo_value&bar=bar_value_1&bar=bar_value_2").build();
    Response response = client.newCall(request).execute();
    assertEquals(Protocol.HTTP_2, response.protocol());
    assertEquals("done", response.body().string());
  }

  @Test
  public void testPost() throws Exception {
    String expectedContent = TestUtils.randomAlphaString(1000);
    CountDownLatch latch = new CountDownLatch(1);
    Buffer postContent = Buffer.buffer();
    server.requestHandler(req -> {
      req.handler(postContent::appendBuffer);
      req.endHandler(v -> {
        req.response().putHeader("content-type", "text/plain").end("");
      });
    })
        .listen(ar -> {
          assertTrue(ar.succeeded());
          latch.countDown();
        });
    awaitLatch(latch);
    OkHttpClient client = createHttp2Client();
    Request request = new Request.Builder()
        .post(RequestBody.create(MediaType.parse("test/plain"), expectedContent))
        .url("https://localhost:4043/")
        .build();
    Response response = client.newCall(request).execute();
    assertEquals(Protocol.HTTP_2, response.protocol());
    assertEquals(expectedContent, postContent.toString());
  }

  @Test
  public void testPostFileUpload() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      Buffer tot = Buffer.buffer();
      req.setExpectMultipart(true);
      req.uploadHandler(upload -> {
        assertEquals("file", upload.name());
        assertEquals("tmp-0.txt", upload.filename());
        assertEquals("image/gif", upload.contentType());
        upload.handler(tot::appendBuffer);
        upload.endHandler(v -> {
          assertEquals(tot, Buffer.buffer("some-content"));
        });
      });
      req.endHandler(v -> {
        assertEquals(0, req.formAttributes().size());
        req.response().putHeader("content-type", "text/plain").end("done");
      });
    })
        .listen(ar -> {
          assertTrue(ar.succeeded());
          latch.countDown();
        });
    awaitLatch(latch);
    OkHttpClient client = createHttp2Client();
    Request request = new Request.Builder()
        .post(new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "tmp-0.txt", RequestBody.create(MediaType.parse("image/gif"), "some-content"))
            .build())
        .url("https://localhost:4043/form")
        .build();
    Response response = client.newCall(request).execute();
    assertEquals(200, response.code());
    assertEquals(Protocol.HTTP_2, response.protocol());
    assertEquals("done", response.body().string());
  }

  @Test
  public void testServerRequestPause() throws Exception {
    String expectedContent = TestUtils.randomAlphaString(1000);
    CountDownLatch latch = new CountDownLatch(1);
    Thread t = Thread.currentThread();
    AtomicBoolean done = new AtomicBoolean();
    Buffer received = Buffer.buffer();
    server.requestHandler(req -> {
      vertx.setPeriodic(1, timerID -> {
        if (t.getState() == Thread.State.WAITING) {
          vertx.cancelTimer(timerID);
          done.set(true);
          // Let some time to accumulate some more buffers
          vertx.setTimer(100, id -> {
            req.resume();
          });
        }
      });
      req.handler(received::appendBuffer);
      req.endHandler(v -> {
        req.response().end("hello");
      });
      req.pause();
    })
        .listen(ar -> {
          assertTrue(ar.succeeded());
          latch.countDown();
        });
    awaitLatch(latch);
    OkHttpClient client = createHttp2Client();
    Buffer sent = Buffer.buffer();
    Request request = new Request.Builder()
        .post(new RequestBody() {
          @Override
          public MediaType contentType() {
            return MediaType.parse("text/plain");
          }

          @Override
          public void writeTo(BufferedSink sink) throws IOException {
            while (!done.get()) {
              sent.appendString(expectedContent);
              sink.write(expectedContent.getBytes());
              sink.flush();
            }
            sink.close();
          }
        })
        .url("https://localhost:4043/")
        .build();
    Response response = client.newCall(request).execute();
    assertEquals(Protocol.HTTP_2, response.protocol());
    assertEquals("hello", response.body().string());
    assertEquals(received, sent);
  }

  @Test
  public void testServerResponseWritability() throws Exception {
    String content = TestUtils.randomAlphaString(1024);
    StringBuilder expected = new StringBuilder();
    CountDownLatch latch = new CountDownLatch(1);
    CompletableFuture<Void> whenFull = new CompletableFuture<>();
    AtomicBoolean drain = new AtomicBoolean();
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.putHeader("content-type", "text/plain");
      resp.setChunked(true);
      vertx.setPeriodic(1, timerID -> {
        if (resp.writeQueueFull()) {
          resp.drainHandler(v -> {
            expected.append("last");
            resp.end(Buffer.buffer("last"));
          });
          vertx.cancelTimer(timerID);
          drain.set(true);
          whenFull.complete(null);
        } else {
          expected.append(content);
          Buffer buf = Buffer.buffer(content);
          resp.write(buf);
        }
      });
    })
        .listen(ar -> {
          assertTrue(ar.succeeded());
          latch.countDown();
        });
    awaitLatch(latch);

    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(4043, "localhost", request -> {
      AtomicInteger toAck = new AtomicInteger();
      int id = request.connection.local().nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, new DefaultHttp2Headers(), 0, true, request.context.newPromise());
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
      whenFull.thenAccept(v -> {
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
  public void testServerResetClientStream() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      req.handler(buf -> {
        req.response().reset(8);
      });
    }).listen(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);

    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(4043, "localhost", request -> {
      int id = request.connection.local().nextStreamId();
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
      encoder.writeHeaders(request.context, id, new DefaultHttp2Headers(), 0, false, request.context.newPromise());
      encoder.writeData(request.context, id, Buffer.buffer("hello").getByteBuf(), 0, false, request.context.newPromise());
    });

    fut.sync();

    await();
  }

  @Test
  public void testClientResetServerStream() throws Exception {

    CountDownLatch latch = new CountDownLatch(1);
    CompletableFuture<Void> bufReceived = new CompletableFuture<>();
    AtomicInteger resetCount = new AtomicInteger();
    server.requestHandler(req -> {
      req.handler(buf -> {
        bufReceived.complete(null);
      });
      req.response().resetHandler(code -> {
        assertEquals((Long) 10L, code);
        assertEquals(0, resetCount.getAndIncrement());
      });
      req.endHandler(v -> {
        assertEquals(1, resetCount.get());
        testComplete();
      });
    })
        .listen(ar -> {
          assertTrue(ar.succeeded());
          latch.countDown();
        });
    awaitLatch(latch);

    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(4043, "localhost", request -> {
      int id = request.connection.local().nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, new DefaultHttp2Headers(), 0, false, request.context.newPromise());
      encoder.writeData(request.context, id, Buffer.buffer("hello").getByteBuf(), 0, false, request.context.newPromise());
      bufReceived.thenAccept(v -> {
        encoder.writeRstStream(request.context, id, 10, request.context.newPromise());
        request.context.flush();
      });
    });

    fut.sync();

    await();
  }

  @Test
  public void testConnectionClose() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      HttpConnection conn = req.connection();
      conn.closeHandler(v -> {
        testComplete();
      });
      req.response().putHeader("Content-Type", "text/plain").end();
    })
        .listen(ar -> {
          assertTrue(ar.succeeded());
          latch.countDown();
        });
    awaitLatch(latch);

    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(4043, "localhost", request -> {
      int id = request.connection.local().nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, new DefaultHttp2Headers(), 0, true, request.context.newPromise());
      request.decoder.frameListener(new Http2FrameAdapter() {
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          request.context.close();
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
    });
    fut.sync();
    await();
  }

  @Test
  public void testPushPromise() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      req.promisePush(HttpMethod.GET, "/wibble", ar -> {
        assertTrue(ar.succeeded());
        HttpServerResponse response = ar.result();
        try {
          response./*putHeader("content-type", "application/plain").*/end("the_content");
        } catch (Exception e) {
          e.printStackTrace();
        }
      });
    })
        .listen(ar -> {
          assertTrue(ar.succeeded());
          latch.countDown();
        });
    awaitLatch(latch);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(4043, "localhost", request -> {
      int id = request.connection.local().nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, new DefaultHttp2Headers(), 0, true, request.context.newPromise());
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
            Http2Headers entries = pushed.get(streamId);
//            assertEquals("application/data", entries.get("content-type").toString());
            assertEquals("GET", entries.method().toString());
            assertEquals("/wibble", entries.path().toString());
            assertEquals("the_content", content);
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
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      req.promisePush(HttpMethod.GET, "/wibble", ar -> {
        assertTrue(ar.succeeded());
        HttpServerResponse response = ar.result();
        response.resetHandler(code -> {
          testComplete();
        });
        response.setChunked(true).write("some_content");
      });
    })
        .listen(ar -> {
          assertTrue(ar.succeeded());
          latch.countDown();
        });
    awaitLatch(latch);
    TestClient client = new TestClient();
    ChannelFuture fut = client.connect(4043, "localhost", request -> {
      int id = request.connection.local().nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, new DefaultHttp2Headers(), 0, true, request.context.newPromise());
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
    int numPushes = 10;
    CountDownLatch latch = new CountDownLatch(1);
    Set<String> pushSent = new HashSet<>();
    server.requestHandler(req -> {
      req.response().setChunked(true).write("abc");
      for (int i = 0; i < numPushes; i++) {
        int val = i;
        String path = "/wibble" + val;
        req.promisePush(HttpMethod.GET, path, ar -> {
          assertTrue(ar.succeeded());
          pushSent.add(path);
          vertx.setTimer(10, id -> {
            ar.result().end("wibble-" + val);
          });
        });
      }
    })
        .listen(ar -> {
          assertTrue(ar.succeeded());
          latch.countDown();
        });
    awaitLatch(latch);
    TestClient client = new TestClient();
    client.settings.maxConcurrentStreams(3);
    ChannelFuture fut = client.connect(4043, "localhost", request -> {
      int id = request.connection.local().nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, new DefaultHttp2Headers(), 0, true, request.context.newPromise());
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
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      req.promisePush(HttpMethod.GET, "/wibble", ar -> {
        assertFalse(ar.succeeded());
        testComplete();
      });
    })
        .listen(ar -> {
          assertTrue(ar.succeeded());
          latch.countDown();
        });
    awaitLatch(latch);
    TestClient client = new TestClient();
    client.settings.maxConcurrentStreams(0);
    ChannelFuture fut = client.connect(4043, "localhost", request -> {
      int id = request.connection.local().nextStreamId();
      Http2ConnectionEncoder encoder = request.encoder;
      encoder.writeHeaders(request.context, id, new DefaultHttp2Headers(), 0, true, request.context.newPromise());
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
}
