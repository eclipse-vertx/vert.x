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
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2Test extends HttpTestBase {


  @Override
  public void setUp() throws Exception {
    super.setUp();

    HttpServerOptions options = new HttpServerOptions()
        .setPort(4043)
        .setHost("localhost")
        .setUseAlpn(true)
        .setSsl(true)
        .addEnabledCipherSuite("TLS_RSA_WITH_AES_128_CBC_SHA") // Non Diffie-helman -> debuggable in wireshark
        .setKeyStoreOptions((JksOptions) getServerCertOptions(KeyCert.JKS));

    server = vertx.createHttpServer(options);

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
  public void testGet() throws Exception {
    String content = TestUtils.randomAlphaString(1000);
    AtomicBoolean requestEnded = new AtomicBoolean();
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
          req.endHandler(v -> {
            requestEnded.set(true);
          });
          req.response().putHeader("content-type", "text/plain").end(content);
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
  public void testResetServerStream() throws Exception {

    CountDownLatch latch = new CountDownLatch(1);
    CompletableFuture<Void> bufReceived = new CompletableFuture<>();
    AtomicInteger resetCount = new AtomicInteger();
    server.requestHandler(req -> {
      req.handler(buf -> {
        bufReceived.complete(null);
      });
      req.resetHandler(code -> {
        assertEquals((Long)10L, code);
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
}
