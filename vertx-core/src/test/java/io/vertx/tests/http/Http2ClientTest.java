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

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.*;
import io.netty.handler.ssl.*;
import io.netty.util.AsciiString;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.Http2StreamPriority;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.test.core.TestUtils;
import io.vertx.test.tls.Cert;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;


public class Http2ClientTest extends HttpClientTest {
  @Override
  public void setUp() throws Exception {
    eventLoopGroups.clear();
    serverOptions = HttpOptionsFactory.createHttp2ServerOptions(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST);
    clientOptions = HttpOptionsFactory.createHttp2ClientOptions();
    super.setUp();
  }

    @Override
  protected StreamPriorityBase generateStreamPriority() {
    return new Http2StreamPriority()
      .setDependency(TestUtils.randomPositiveInt())
      .setWeight((short) TestUtils.randomPositiveInt(255))
      .setExclusive(TestUtils.randomBoolean());
  }

  @Override
  protected StreamPriorityBase defaultStreamPriority() {
    return new Http2StreamPriority()
      .setDependency(0)
      .setWeight(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT)
      .setExclusive(false);
  }

  @Override
  protected void configureDomainSockets() throws Exception {
    // Nope
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    for (EventLoopGroup eventLoopGroup : eventLoopGroups) {
      eventLoopGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS);
    }
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return serverOptions;
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return clientOptions;
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

  private AbstractBootstrap createH2Server(BiFunction<Http2ConnectionDecoder, Http2ConnectionEncoder, Http2FrameListener> handler) {
    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.channel(NioServerSocketChannel.class);
    NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    eventLoopGroups.add(eventLoopGroup);
    bootstrap.group(eventLoopGroup);
    bootstrap.childHandler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        SslContext sslContext = SslContextBuilder
          .forServer(Cert.SERVER_JKS.get().getKeyManagerFactory(vertx))
          .applicationProtocolConfig(new ApplicationProtocolConfig(
            ApplicationProtocolConfig.Protocol.ALPN,
            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
            HttpVersion.HTTP_2.alpnName(), HttpVersion.HTTP_1_1.alpnName()
          ))
          .build();
        SslHandler sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT);
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

  private ServerBootstrap createH2CServer(BiFunction<Http2ConnectionDecoder, Http2ConnectionEncoder, Http2FrameListener> handler, Handler<HttpServerUpgradeHandler.UpgradeEvent> upgradeHandler, boolean upgrade) {
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
          ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
              if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent) {
                upgradeHandler.handle((HttpServerUpgradeHandler.UpgradeEvent) evt);
              }
              super.userEventTriggered(ctx, evt);
            }
          });
        } else {
          Http2ConnectionHandler clientHandler = createHttpConnectionHandler(handler);
          ch.pipeline().addLast("handler", clientHandler);
        }
      }
    });
    return bootstrap;
  }

  @Override
  protected AbstractBootstrap createServerForGet() {
    return createH2Server((decoder, encoder) -> new Http2EventAdapter() {
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
  }

  @Override
  protected AbstractBootstrap createServerForClientResetServerStream(boolean endServer) {
    return createH2Server((decoder, encoder) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        encoder.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("200"), 0, false, ctx.newPromise());
        ctx.flush();
      }

      @Override
      public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
        encoder.writeData(ctx, streamId, Unpooled.copiedBuffer("pong", 0, 4, StandardCharsets.UTF_8), 0, endServer, ctx.newPromise());
        ctx.flush();
        return super.onDataRead(ctx, streamId, data, padding, endOfStream);
      }

      @Override
      public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
        vertx.runOnContext(v -> {
          assertEquals(10L, errorCode);
          complete();
        });
      }
    });
  }

  @Override
  protected AbstractBootstrap createServerForStreamError() {
    return createH2Server((dec, enc) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        enc.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("200"), 0, false, ctx.newPromise());
        // Send a corrupted frame on purpose to check we get the corresponding error in the request exception handler
        // the error is : greater padding value 0c -> 1F
        // ChannelFuture a = encoder.frameWriter().writeData(request.context, id, Buffer.buffer("hello").getByteBuf(), 12, false, request.context.newPromise());
        // normal frame    : 00 00 12 00 08 00 00 00 03 0c 68 65 6c 6c 6f 00 00 00 00 00 00 00 00 00 00 00 00
        // corrupted frame : 00 00 12 00 08 00 00 00 03 1F 68 65 6c 6c 6f 00 00 00 00 00 00 00 00 00 00 00 00
        ctx.channel().write(BufferInternal.buffer(new byte[]{
          0x00, 0x00, 0x12, 0x00, 0x08, 0x00, 0x00, 0x00, (byte) (streamId & 0xFF), 0x1F, 0x68, 0x65, 0x6c, 0x6c,
          0x6f, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        }).getByteBuf());
        ctx.flush();
      }
    });
  }

  @Override
  protected AbstractBootstrap createServerForConnectionDecodeError() {
    return createH2Server((dec, enc) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        enc.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("200"), 0, false, ctx.newPromise());
        enc.frameWriter().writeRstStream(ctx, 10, 0, ctx.newPromise());
        ctx.flush();
      }
    });
  }

  @Override
  protected AbstractBootstrap createServerForInvalidServerResponse() {
    return createH2Server((dec, enc) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        enc.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("xyz"), 0, false, ctx.newPromise());
        ctx.flush();
      }
    });
  }

  @Override
  protected AbstractBootstrap createServerForClearText(List<String> requests, boolean withUpgrade) {
    return createH2CServer((dec, enc) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        requests.add(headers.method().toString());
        enc.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("200"), 0, true, ctx.newPromise());
        ctx.flush();
      }
    }, upgrade -> {
      requests.add(upgrade.upgradeRequest().method().name());
    }, withUpgrade);
  }

  @Override
  protected AbstractBootstrap createServerForConnectionWindowSize() {
    return createH2Server((decoder, encoder) -> new Http2EventAdapter() {
      @Override
      public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) throws Http2Exception {
        vertx.runOnContext(v -> {
          assertEquals(65535, windowSizeIncrement);
          testComplete();
        });
      }
    });
  }

  @Override
  protected AbstractBootstrap createServerForUpdateConnectionWindowSize() {
    return createH2Server((decoder, encoder) -> new Http2EventAdapter() {
      @Override
      public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) throws Http2Exception {
        vertx.runOnContext(v -> {
          assertEquals(65535, windowSizeIncrement);
          testComplete();
        });
      }
    });
  }

  @Override
  protected AbstractBootstrap createServerForStreamPriority(StreamPriorityBase requestStreamPriority, StreamPriorityBase responseStreamPriority) {
    return createH2Server((decoder, encoder) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        vertx.runOnContext(v -> {
          assertEquals(requestStreamPriority.getDependency(), streamDependency);
          assertEquals(requestStreamPriority.getWeight(), weight);
          assertEquals(requestStreamPriority.isExclusive(), exclusive);
          encoder.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("200"), responseStreamPriority.getDependency(), responseStreamPriority.getWeight(), responseStreamPriority.isExclusive(), 0, true, ctx.newPromise());
          ctx.flush();
          if (endStream)
            complete();
        });
      }
    });
  }

  @Override
  protected AbstractBootstrap createServerForStreamPriorityChange(StreamPriorityBase requestStreamPriority, StreamPriorityBase responseStreamPriority, StreamPriorityBase requestStreamPriority2, StreamPriorityBase responseStreamPriority2) {
    return createH2Server((decoder, encoder_) -> {
      Http2ConnectionEncoder encoder = (Http2ConnectionEncoder) encoder_;

      return new Http2EventAdapter() {
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
          if (endOfStream) {
            encoder.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("200"), responseStreamPriority.getDependency(), responseStreamPriority.getWeight(), responseStreamPriority.isExclusive(), 0, false, ctx.newPromise());
            ctx.flush();
            encoder.writePriority(ctx, streamId, responseStreamPriority2.getDependency(), responseStreamPriority2.getWeight(), responseStreamPriority2.isExclusive(), ctx.newPromise());
            ctx.flush();
            encoder.writeData(ctx, streamId, BufferInternal.buffer("hello").getByteBuf(), 0, true, ctx.newPromise());
            ctx.flush();
            vertx.runOnContext(v -> {
              complete();
            });
          }
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }

      };
    });
  }

  @Override
  protected AbstractBootstrap createServerForClientStreamPriorityNoChange(StreamPriorityBase streamPriority, Promise<Void> latch) {
    return createH2Server((decoder, encoder) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        vertx.runOnContext(v -> {
          assertEquals(streamPriority.getDependency(), streamDependency);
          assertEquals(streamPriority.getWeight(), weight);
          assertEquals(streamPriority.isExclusive(), exclusive);
          assertFalse(endStream);
          latch.complete();
        });
        encoder.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("200"), 0, true, ctx.newPromise());
        ctx.flush();
      }

      @Override
      public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) throws Http2Exception {
        fail("Priority frame should not be sent");
      }

      @Override
      public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
        if (endOfStream) {
          vertx.runOnContext(v -> {
            complete();
          });
        }
        return super.onDataRead(ctx, streamId, data, padding, endOfStream);
      }
    });
  }

  @Override
  protected AbstractBootstrap createServerForServerStreamPriorityNoChange(StreamPriorityBase streamPriority) {
    return createH2Server((decoder, encoder) -> new Http2EventAdapter() {
      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        encoder.writeHeaders(ctx, streamId, new DefaultHttp2Headers().status("200"), streamPriority.getDependency(), streamPriority.getWeight(), streamPriority.isExclusive(), 0, false, ctx.newPromise());
        encoder.writePriority(ctx, streamId, streamPriority.getDependency(), streamPriority.getWeight(), streamPriority.isExclusive(), ctx.newPromise());
        encoder.writeData(ctx, streamId, BufferInternal.buffer("hello").getByteBuf(), 0, true, ctx.newPromise());
        ctx.flush();
      }

      @Override
      public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) throws Http2Exception {
        fail("Priority frame should not be sent");
      }
    });
  }
}
