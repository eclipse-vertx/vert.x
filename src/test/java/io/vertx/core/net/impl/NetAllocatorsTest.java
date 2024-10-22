/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.vertx.core.Context;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.PartialPooledByteBufAllocator;
import io.vertx.core.buffer.impl.VertxByteBufAllocator;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;

public class NetAllocatorsTest extends VertxTestBase {

  private SocketAddress testAddress;
  private NetServer server;
  private NetClient client;
  private File tmp;

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    if (USE_DOMAIN_SOCKETS) {
      assertTrue("Native transport not enabled", USE_NATIVE_TRANSPORT);
      tmp = TestUtils.tmpFile(".sock");
      testAddress = SocketAddress.domainSocketAddress(tmp.getAbsolutePath());
    } else {
      testAddress = SocketAddress.inetSocketAddress(1234, "localhost");
    }
    client = vertx.createNetClient(new NetClientOptions().setConnectTimeout(1000));
    server = vertx.createNetServer();
  }

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.getAddressResolverOptions().setHostsValue(Buffer.buffer("" +
        "127.0.0.1 localhost\n" +
        "127.0.0.1 host1\n" +
        "127.0.0.1 host2.com\n" +
        "127.0.0.1 example.com"));
    return options;
  }

  @Override
  protected void tearDown() throws Exception {
    if (tmp != null) {
      tmp.delete();
    }
    super.tearDown();
  }

  @Test
  public void testServerAllocatorNoSSL() throws Exception {
    server.close();
    server = vertx.createNetServer(new NetServerOptions()
      .setPort(1234)
      .setHost("localhost"));
    testServerAllocator(new HttpClientOptions(), false,
      PooledByteBufAllocator.DEFAULT, PooledByteBufAllocator.DEFAULT, false);
  }

  @Test
  public void testHeapPoolingServerAllocatorJdkSSL() throws Exception {
    server.close();
    server = vertx.createNetServer(new NetServerOptions()
      .setPort(1234)
      .setHost("localhost")
      .setSsl(true)
      .setSslEngineOptions(new JdkSSLEngineOptions().setPooledHeapBuffers(true))
      .setKeyStoreOptions(Cert.SERVER_JKS.get()));
    testServerAllocator(new HttpClientOptions()
        .setSsl(true)
        .setTrustStoreOptions(Trust.SERVER_JKS.get()), true,
      // the JDK SSL engine wrapping buffer is heap-based, but the output one not, see:
      // see https://github.com/netty/netty/blob/f377e7e23f71fbf1e682bfd5b69b8720338ee8b9/handler/src/main/java/io/netty/handler/ssl/SslHandler.java#L2407
      // It uses the allocator's buffer method, which is direct-based on PooledByteBufAllocator.DEFAULT
      PooledByteBufAllocator.DEFAULT, PooledByteBufAllocator.DEFAULT, false);
  }

  @Test
  public void testServerAllocatorJdkSSL() throws Exception {
    server.close();
    server = vertx.createNetServer(new NetServerOptions()
      .setPort(1234)
      .setHost("localhost")
      .setSsl(true)
      .setSslEngineOptions(new JdkSSLEngineOptions())
      .setKeyStoreOptions(Cert.SERVER_JKS.get()));
    testServerAllocator(new HttpClientOptions()
        .setSsl(true)
        .setTrustStoreOptions(Trust.SERVER_JKS.get()), true,
      VertxByteBufAllocator.UNPOOLED_ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE, true);
  }

  @Test
  public void testServerAllocatorOpenSSL() throws Exception {
    Assume.assumeTrue(OpenSSLEngineOptions.isAvailable());
    server.close();
    server = vertx.createNetServer(new NetServerOptions()
      .setPort(1234)
      .setHost("localhost")
      .setSsl(true)
      .setSslEngineOptions(new OpenSSLEngineOptions())
      .setKeyStoreOptions(Cert.SERVER_JKS.get()));
    testServerAllocator(new HttpClientOptions()
        .setSsl(true)
        .setTrustStoreOptions(Trust.SERVER_JKS.get()), true,
      VertxByteBufAllocator.POOLED_ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE, false);
  }

  private void testServerAllocator(HttpClientOptions clientOptions, boolean expectSSL,
                                   ByteBufAllocator bufferAllocator, ByteBufAllocator channelAllocator,
                                   boolean expectHeapBuffer) throws Exception {
    waitFor(2);
    server.connectHandler(so -> {
      NetSocketInternal internal = (NetSocketInternal) so;
      assertEquals(expectSSL, internal.isSsl());
      ChannelHandlerContext chctx = internal.channelHandlerContext();
      ChannelPipeline pipeline = chctx.pipeline();
      pipeline.addBefore("handler", "http", new HttpServerCodec());
      // add a new handler which feeds the raw buffer to the http handler: this should receive the buffer
      // from the SSL handler, if configured
      pipeline.addBefore("http", "raw", new io.netty.channel.ChannelInboundHandlerAdapter() {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
          assertTrue(msg instanceof ByteBuf);
          ByteBuf byteBuf = (ByteBuf) msg;
          assertSame(bufferAllocator, byteBuf.alloc());
          assertSame(channelAllocator, ctx.channel().config().getAllocator());
          assertTrue(expectHeapBuffer == byteBuf.hasArray());
          super.channelRead(ctx, msg);
        }
      });
      internal.handler(buff -> fail());
      internal.messageHandler(obj -> {
        if (obj instanceof LastHttpContent) {
          DefaultFullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.copiedBuffer("Hello World", StandardCharsets.UTF_8));
          response.headers().set(HttpHeaderNames.CONTENT_LENGTH, "11");
          internal.writeMessage(response, onSuccess(v -> complete()));
        }
      });
    });
    startServer(SocketAddress.inetSocketAddress(1234, "localhost"));
    HttpClient client = vertx.createHttpClient(clientOptions);
    client.request(io.vertx.core.http.HttpMethod.GET, 1234, "localhost", "/somepath", onSuccess(req -> {
      req.send(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        resp.body(onSuccess(body -> {
          assertEquals("Hello World", body.toString());
          complete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testClientAllocatorNoSSL() throws Exception {
    testClientAllocator(new HttpServerOptions()
      .setHost("localhost")
      .setPort(1234), false,
      VertxByteBufAllocator.POOLED_ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE, false);
  }

  @Test
  public void testHeapPoolingClientAllocatorJdkSSL() throws Exception {
    client.close();
    client = vertx.createNetClient(new NetClientOptions()
      .setSsl(true)
      .setSslEngineOptions(new JdkSSLEngineOptions().setPooledHeapBuffers(true))
      .setHostnameVerificationAlgorithm("")
      .setTrustStoreOptions(Trust.SERVER_JKS.get()));
    testClientAllocator(new HttpServerOptions()
      .setHost("localhost")
      .setPort(1234)
      .setSsl(true)
      .setKeyStoreOptions(Cert.SERVER_JKS.get()), true,
      // the JDK SSL engine wrapping buffer is heap-based, but the output one not, see:
      // see https://github.com/netty/netty/blob/f377e7e23f71fbf1e682bfd5b69b8720338ee8b9/handler/src/main/java/io/netty/handler/ssl/SslHandler.java#L2407
      // It uses the allocator's buffer method, which is direct-based on PooledByteBufAllocator.DEFAULT
      PooledByteBufAllocator.DEFAULT, PooledByteBufAllocator.DEFAULT, false);
  }

  @Test
  public void testClientAllocatorJdkSSL() throws Exception {
    client.close();
    client = vertx.createNetClient(new NetClientOptions()
      .setSsl(true)
        .setSslEngineOptions(new JdkSSLEngineOptions())
      .setHostnameVerificationAlgorithm("")
      .setTrustStoreOptions(Trust.SERVER_JKS.get()));
    testClientAllocator(new HttpServerOptions()
      .setHost("localhost")
      .setPort(1234)
      .setSsl(true)
      .setKeyStoreOptions(Cert.SERVER_JKS.get()), true,
      VertxByteBufAllocator.UNPOOLED_ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE, true);
  }

  @Test
  public void testClientAllocatorOpenSSL() throws Exception {
    Assume.assumeTrue(OpenSSLEngineOptions.isAvailable());
    client.close();
    client = vertx.createNetClient(new NetClientOptions()
      .setSsl(true)
      .setSslEngineOptions(new OpenSSLEngineOptions())
      .setHostnameVerificationAlgorithm("")
      .setTrustStoreOptions(Trust.SERVER_JKS.get()));
    testClientAllocator(new HttpServerOptions()
        .setHost("localhost")
        .setPort(1234)
        .setSsl(true)
        .setKeyStoreOptions(Cert.SERVER_JKS.get()), true,
      VertxByteBufAllocator.POOLED_ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE, false);
  }

  private void testClientAllocator(HttpServerOptions options,
                                   boolean expectSSL,
                                   ByteBufAllocator expectedBufferAllocator,
                                   ByteBufAllocator expectedChannelAllocator,
                                   boolean expectHeapBuffer) throws Exception {
    waitFor(2);
    HttpServer server = vertx.createHttpServer(options);
    server.requestHandler(req -> {
      req.response().end("Hello World"); });
    CountDownLatch latch = new CountDownLatch(1);
    server.listen(onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    client.connect(1234, "localhost", onSuccess(so -> {
      NetSocketInternal soInt = (NetSocketInternal) so;
      assertEquals(expectSSL, soInt.isSsl());
      ChannelHandlerContext chctx = soInt.channelHandlerContext();
      ChannelPipeline pipeline = chctx.pipeline();
      pipeline.addBefore("handler", "http", new HttpClientCodec());
      // add a new handler which feeds the raw buffer to the http handler: this should receive the buffer
      // from the SSL handler, if configured
      pipeline.addBefore("http", "raw", new io.netty.channel.ChannelInboundHandlerAdapter() {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
          assertTrue(msg instanceof ByteBuf);
          ByteBuf byteBuf = (ByteBuf) msg;
          assertSame(expectedBufferAllocator, byteBuf.alloc());
          assertSame(expectedChannelAllocator, ctx.channel().config().getAllocator());
          assertTrue(expectHeapBuffer == byteBuf.hasArray());
          super.channelRead(ctx, msg);
          complete();
        }
      });
      soInt.handler(buff -> fail());
      soInt.writeMessage(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/somepath"), onSuccess(v -> complete()));
    }));
    await();
  }

  protected void startServer(SocketAddress remoteAddress) throws Exception {
    startServer(remoteAddress, vertx.getOrCreateContext());
  }

  protected void startServer(SocketAddress remoteAddress, Context context) throws Exception {
    startServer(remoteAddress, context, server);
  }

  protected void startServer(SocketAddress remoteAddress, Context context, NetServer server) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    context.runOnContext(v -> {
      server.listen(remoteAddress, onSuccess(s -> latch.countDown()));
    });
    awaitLatch(latch);
  }

  protected void startServer() throws Exception {
    startServer(testAddress, vertx.getOrCreateContext());
  }

  protected void startServer(NetServer server) throws Exception {
    startServer(testAddress, vertx.getOrCreateContext(), server);
  }

  protected void startServer(Context context) throws Exception {
    startServer(testAddress, context, server);
  }

  protected void startServer(Context context, NetServer server) throws Exception {
    startServer(testAddress, context, server);
  }
}
