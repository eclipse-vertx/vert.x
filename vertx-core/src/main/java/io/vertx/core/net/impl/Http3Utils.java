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

package io.vertx.core.net.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3ClientConnectionHandler;
import io.netty.incubator.codec.http3.Http3FrameToHttpObjectCodec;
import io.netty.incubator.codec.http3.Http3ServerConnectionHandler;
import io.netty.incubator.codec.http3.Http3SettingsFrame;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.internal.PromiseInternal;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3Utils {

  private static final InternalLogger logger = InternalLoggerFactory.getInstance(Http3Utils.class);

  public static Http3ServerConnectionHandlerBuilder newServerConnectionHandler() {
    return new Http3ServerConnectionHandlerBuilder();
  }

  public static Http3ClientConnectionHandlerBuilder newClientConnectionHandler() {
    return new Http3ClientConnectionHandlerBuilder();
  }

  public static ChannelFuture newDatagramChannel(EventLoop eventLoop, InetSocketAddress remoteAddress,
                                                 ChannelHandler handler) {
    return new Bootstrap()
      .resolver(DefaultAddressResolverGroup.INSTANCE)
      .group(eventLoop)
      .channel(NioDatagramChannel.class)
      .handler(handler)
      .connect(remoteAddress);
  }

  public static Future<QuicChannel> newQuicChannel(NioDatagramChannel channel, ChannelHandler handler) {
    return QuicChannel.newBootstrap(channel)
      .handler(handler)
      .localAddress(channel.localAddress())
      .remoteAddress(channel.remoteAddress())
      .connect();
  }

  public static Future<QuicChannel> newQuicChannel(NioDatagramChannel channel, Handler<QuicChannel> handler) {
    ChannelInitializer<QuicChannel> channelHandler = new ChannelInitializer<>() {
      @Override
      protected void initChannel(QuicChannel ch) {
        handler.handle(ch);
      }
    };
    return newQuicChannel(channel, channelHandler);
  }

  public static Http3FrameToHttpObjectCodec newHttp3ClientFrameToHttpObjectCodec() {
    return new Http3FrameToHttpObjectCodec(false);
  }

  public static Http3FrameToHttpObjectCodec newHttp3ServerFrameToHttpObjectCodec() {
    return new Http3FrameToHttpObjectCodec(true);
  }

  public static io.vertx.core.Future<QuicStreamChannel> newRequestStream(QuicChannel channel,
                                                                         Handler<QuicStreamChannel> handler) {
    PromiseInternal<QuicStreamChannel> listener = (PromiseInternal) Promise.promise();

    Http3.newRequestStream(channel, new ChannelInitializer<QuicStreamChannel>() {
      @Override
      protected void initChannel(QuicStreamChannel quicStreamChannel) {
        handler.handle(quicStreamChannel);
      }
    }).addListener(listener);
    return listener;
  }

  public static ChannelHandler newClientSslContext() {
    QuicSslContext context = QuicSslContextBuilder.forClient()
      .trustManager(InsecureTrustManagerFactory.INSTANCE)
      .applicationProtocols(Http3.supportedApplicationProtocols()).build();
    return Http3.newQuicClientCodecBuilder()
      .sslContext(context)
      .datagram(2000000, 2000000)
      .maxIdleTimeout(5000, TimeUnit.HOURS)
      .initialMaxData(10000000)
      .initialMaxStreamDataBidirectionalLocal(1000000)
      .build();
  }

  public static <V> V getResultOrThrow(Future<V> future) {
    if (!future.isSuccess()) {
      logger.error("There is an error in future.", future.cause());
      throw new RuntimeException(future.cause());
    }
    try {
      if (future instanceof ChannelFuture) {
        return (V) ((ChannelPromise) future).channel();
      }
      return future.get();
    } catch (InterruptedException | ExecutionException e) {
      logger.error("There is an error in future.", e);
      throw new RuntimeException(e);
    }
  }

  public static class MyChannelInitializer extends ChannelInitializer<QuicChannel> {
    private final ChannelHandler[] handlers;

    public MyChannelInitializer(ChannelHandler... handlers) {
      this.handlers = handlers;
    }

    @Override
    protected void initChannel(QuicChannel ch) {
      ch.pipeline().addLast(handlers);
    }
  }

  public static class PrinterChannelHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      ByteBuf msg0 = (ByteBuf) msg;
      byte[] arr = new byte[msg0.readableBytes()];
      msg0.copy().readBytes(arr);
      logger.info("Received msg is: {}", new String(arr));
      super.channelRead(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      logger.error(cause);
      ctx.close();
    }
  }

  public static class Http3ServerConnectionHandlerBuilder {
    private Handler<QuicStreamChannel> requestStreamHandler;
    private ChannelHandler inboundControlStreamHandler;
    private LongFunction<ChannelHandler> unknownInboundStreamHandlerFactory;
    private Http3SettingsFrame localSettings;
    private boolean disableQpackDynamicTable = true;

    public Http3ServerConnectionHandlerBuilder requestStreamHandler(Handler<QuicStreamChannel> requestStreamHandler) {
      this.requestStreamHandler = requestStreamHandler;
      return this;
    }

    public Http3ServerConnectionHandlerBuilder inboundControlStreamHandler(ChannelHandler inboundControlStreamHandler) {
      this.inboundControlStreamHandler = inboundControlStreamHandler;
      return this;
    }

    public Http3ServerConnectionHandlerBuilder unknownInboundStreamHandlerFactory(LongFunction<ChannelHandler> unknownInboundStreamHandlerFactory) {
      this.unknownInboundStreamHandlerFactory = unknownInboundStreamHandlerFactory;
      return this;
    }

    public Http3ServerConnectionHandlerBuilder localSettings(Http3SettingsFrame localSettings) {
      this.localSettings = localSettings;
      return this;
    }

    public Http3ServerConnectionHandlerBuilder disableQpackDynamicTable(boolean disableQpackDynamicTable) {
      this.disableQpackDynamicTable = disableQpackDynamicTable;
      return this;
    }

    public Http3ServerConnectionHandler build() {
      return new Http3ServerConnectionHandler(new ChannelInitializer<QuicStreamChannel>() {
        @Override
        protected void initChannel(QuicStreamChannel streamChannel) {
          requestStreamHandler.handle(streamChannel);
        }
      }, inboundControlStreamHandler, unknownInboundStreamHandlerFactory, localSettings, disableQpackDynamicTable);
    }
  }

  public static class Http3ClientConnectionHandlerBuilder {
    private ChannelHandler inboundControlStreamHandler;
    private LongFunction<ChannelHandler> pushStreamHandlerFactory;
    private LongFunction<ChannelHandler> unknownInboundStreamHandlerFactory;
    private Http3SettingsFrame localSettings;
    private boolean disableQpackDynamicTable = true;

    public Http3ClientConnectionHandlerBuilder inboundControlStreamHandler(ChannelHandler inboundControlStreamHandler) {
      this.inboundControlStreamHandler = inboundControlStreamHandler;
      return this;
    }

    public Http3ClientConnectionHandlerBuilder pushStreamHandlerFactory(LongFunction<ChannelHandler> pushStreamHandlerFactory) {
      this.pushStreamHandlerFactory = pushStreamHandlerFactory;
      return this;
    }

    public Http3ClientConnectionHandlerBuilder unknownInboundStreamHandlerFactory(LongFunction<ChannelHandler> unknownInboundStreamHandlerFactory) {
      this.unknownInboundStreamHandlerFactory = unknownInboundStreamHandlerFactory;
      return this;
    }

    public Http3ClientConnectionHandlerBuilder localSettings(Http3SettingsFrame localSettings) {
      this.localSettings = localSettings;
      return this;
    }

    public Http3ClientConnectionHandlerBuilder disableQpackDynamicTable(boolean disableQpackDynamicTable) {
      this.disableQpackDynamicTable = disableQpackDynamicTable;
      return this;
    }

    public Http3ClientConnectionHandler build() {
      return new Http3ClientConnectionHandler(inboundControlStreamHandler, pushStreamHandlerFactory,
        unknownInboundStreamHandlerFactory, localSettings, disableQpackDynamicTable);
    }
  }
}
