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
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.codec.http3.DefaultHttp3GoAwayFrame;
import io.netty.handler.codec.http3.DefaultHttp3SettingsFrame;
import io.netty.handler.codec.http3.DefaultHttp3UnknownFrame;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3ClientConnectionHandler;
import io.netty.handler.codec.http3.Http3ConnectionHandler;
import io.netty.handler.codec.http3.Http3FrameToHttpObjectCodec;
import io.netty.handler.codec.http3.Http3GoAwayFrame;
import io.netty.handler.codec.http3.Http3ServerConnectionHandler;
import io.netty.handler.codec.http3.Http3SettingsFrame;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicCodecBuilder;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicSslEngine;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.QuicOptions;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongFunction;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3Utils {

  private static final Logger log = LoggerFactory.getLogger(Http3Utils.class);

  public static Http3ServerConnectionHandlerBuilder newServerConnectionHandlerBuilder() {
    return new Http3ServerConnectionHandlerBuilder();
  }

  public static Http3ClientConnectionHandlerBuilder newClientConnectionHandlerBuilder() {
    return new Http3ClientConnectionHandlerBuilder();
  }

  public static Http3FrameToHttpObjectCodec newClientFrameToHttpObjectCodec() {
    return new Http3FrameToHttpObjectCodec(false);
  }

  public static Http3FrameToHttpObjectCodec newServerFrameToHttpObjectCodec() {
    return new Http3FrameToHttpObjectCodec(true);
  }

  public static List<String> supportedApplicationProtocols() {
    return List.of(Http3.supportedApplicationProtocols());
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

  public static QuicCodecBuilderInitializer createServerQuicCodecBuilderInitializer(QuicOptions quicOptions, ChannelHandler handler) {
    return new QuicCodecBuilderInitializer() {
      @Override
      public void initServerCodecBuilder(QuicServerCodecBuilder quicServerCodecBuilder) {
        configureQuicCodecBuilder(quicServerCodecBuilder, quicOptions)
          .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
          .handler(handler);
      }
    };
  }

  public static QuicCodecBuilderInitializer createClientQuicCodecBuilderInitializer(QuicOptions quicOptions) {
    return new QuicCodecBuilderInitializer() {
      @Override
      public void initClientCodecBuilder(QuicClientCodecBuilder quicClientCodecBuilder) {
        configureQuicCodecBuilder(quicClientCodecBuilder, quicOptions);
      }
    };
  }

  public static QuicSslHandlerWrapper newQuicServerSslHandler(QuicSslEngine sslEngine, Executor delegatedTaskExecutor, SslContext sslContext, QuicCodecBuilderInitializer initializer) {
    ChannelHandler handler = newQuicServerHandler(delegatedTaskExecutor, (QuicSslContext) sslContext, quicChannel -> sslEngine, initializer).build();
    return new QuicSslHandlerWrapper(sslEngine, delegatedTaskExecutor, handler);
  }

  public static QuicServerCodecBuilder newQuicServerHandler(Executor delegatedTaskExecutor, QuicSslContext sslContext, Function<QuicChannel, ? extends QuicSslEngine> sslEngineProvider, QuicCodecBuilderInitializer initializer) {
    QuicServerCodecBuilder quicCodecBuilder = Http3.newQuicServerCodecBuilder();
    initializer.initServerCodecBuilder(quicCodecBuilder);
    return quicCodecBuilder
      .sslTaskExecutor(delegatedTaskExecutor)
      .sslContext(sslContext)
      .sslEngineProvider(sslEngineProvider);
  }

  public static QuicSslHandlerWrapper newQuicClientSslHandler(QuicSslEngine engine, Executor delegatedTaskExecutor, SslContext sslContext, QuicCodecBuilderInitializer initializer) {
    QuicClientCodecBuilder quicCodecBuilder = Http3.newQuicClientCodecBuilder();
    initializer.initClientCodecBuilder(quicCodecBuilder);
    ChannelHandler handler = quicCodecBuilder
      .sslTaskExecutor(delegatedTaskExecutor)
      .sslContext((QuicSslContext) sslContext)
      .sslEngineProvider(quicChannel -> engine)
      .build();
    return new QuicSslHandlerWrapper(engine, delegatedTaskExecutor, handler);
  }

  public static <T extends QuicCodecBuilder<T>> T configureQuicCodecBuilder(T quicCodecBuilder, QuicOptions quicOptions) {
    quicCodecBuilder
      // Enabling this option allows sending unreliable, connectionless data over QUIC
      // via QUIC datagrams. It is required for VertxHandler and net socket to function properly.
      .datagram(2000000, 2000000)

      .maxIdleTimeout(quicOptions.getSslHandshakeTimeout(), quicOptions.getSslHandshakeTimeoutUnit())
      .initialMaxData(quicOptions.getHttp3InitialMaxData())
      .initialMaxStreamsBidirectional(quicOptions.getHttp3InitialMaxStreamsBidirectional())
      .initialMaxStreamDataBidirectionalLocal(quicOptions.getHttp3InitialMaxStreamDataBidirectionalLocal())
      .initialMaxStreamDataBidirectionalRemote(quicOptions.getHttp3InitialMaxStreamDataBidirectionalRemote())
      .initialMaxStreamsUnidirectional(quicOptions.getHttp3InitialMaxStreamsUnidirectional())
      .initialMaxStreamDataUnidirectional(quicOptions.getHttp3InitialMaxStreamDataUnidirectional())
    ;
    return quicCodecBuilder;
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
      log.info(String.format("Received msg is: %s", new String(arr)));
      super.channelRead(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      log.error(cause);
      ctx.close();
    }
  }

  public abstract static class Http3ConnectionHandlerBuilderBase {
    protected String agentType;
    protected Handler<Http3GoAwayFrame> http3GoAwayFrameHandler;
    protected Handler<Http3SettingsFrame> http3SettingsFrameHandler;
    protected LongFunction<ChannelHandler> unknownInboundStreamHandlerFactory;
    protected Http3SettingsFrame localSettings;
    protected boolean disableQpackDynamicTable = true;

    private Http3ConnectionHandlerBuilderBase() {
    }

    public Http3ConnectionHandlerBuilderBase unknownInboundStreamHandlerFactory(LongFunction<ChannelHandler> unknownInboundStreamHandlerFactory) {
      this.unknownInboundStreamHandlerFactory = unknownInboundStreamHandlerFactory;
      return this;
    }

    public Http3ConnectionHandlerBuilderBase localSettings(Http3SettingsFrame localSettings) {
      this.localSettings = localSettings;
      return this;
    }

    public Http3ConnectionHandlerBuilderBase disableQpackDynamicTable(boolean disableQpackDynamicTable) {
      this.disableQpackDynamicTable = disableQpackDynamicTable;
      return this;
    }

    public Http3ConnectionHandlerBuilderBase agentType(String agentType) {
      this.agentType = agentType;
      return this;
    }

    public Http3ConnectionHandlerBuilderBase http3GoAwayFrameHandler(Handler<Http3GoAwayFrame> http3GoAwayFrameHandler) {
      this.http3GoAwayFrameHandler = http3GoAwayFrameHandler;
      return this;
    }

    public Http3ConnectionHandlerBuilderBase http3SettingsFrameHandler(Handler<Http3SettingsFrame> http3SettingsFrameHandler) {
      this.http3SettingsFrameHandler = http3SettingsFrameHandler;
      return this;
    }

    Http3ControlStreamChannelHandler buildHttp3ControlStreamChannelHandler() {
      return new Http3ControlStreamChannelHandler()
        .http3GoAwayFrameHandler(http3GoAwayFrameHandler)
        .http3SettingsFrameHandler(http3SettingsFrameHandler)
        .agentType(agentType);
    }

    public abstract Http3ConnectionHandler build();
  }

  public static class Http3ServerConnectionHandlerBuilder extends Http3ConnectionHandlerBuilderBase {
    private Handler<QuicStreamChannel> requestStreamHandler;

    private Http3ServerConnectionHandlerBuilder() {
    }

    public Http3ServerConnectionHandlerBuilder requestStreamHandler(Handler<QuicStreamChannel> requestStreamHandler) {
      this.requestStreamHandler = requestStreamHandler;
      return this;
    }

    public Http3ServerConnectionHandler build() {
      return new Http3ServerConnectionHandler(new ChannelInitializer<QuicStreamChannel>() {
        @Override
        protected void initChannel(QuicStreamChannel streamChannel) {
          requestStreamHandler.handle(streamChannel);
        }
      }, buildHttp3ControlStreamChannelHandler(), unknownInboundStreamHandlerFactory, localSettings, disableQpackDynamicTable);
    }
  }

  public static class Http3ClientConnectionHandlerBuilder extends Http3ConnectionHandlerBuilderBase {
    private LongFunction<ChannelHandler> pushStreamHandlerFactory;

    private Http3ClientConnectionHandlerBuilder() {
    }

    public Http3ClientConnectionHandlerBuilder pushStreamHandlerFactory(LongFunction<ChannelHandler> pushStreamHandlerFactory) {
      this.pushStreamHandlerFactory = pushStreamHandlerFactory;
      return this;
    }

    public Http3ClientConnectionHandler build() {
      return new Http3ClientConnectionHandler(buildHttp3ControlStreamChannelHandler(), pushStreamHandlerFactory,
        unknownInboundStreamHandlerFactory, localSettings, disableQpackDynamicTable);
    }
  }

  private final static class Http3ControlStreamChannelHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(Http3ControlStreamChannelHandler.class);

    private Http3SettingsFrame http3SettingsFrame;
    private boolean settingsRead;
    private String agentType;
    private Handler<Http3SettingsFrame> http3SettingsFrameHandler;
    private Handler<Http3GoAwayFrame> http3GoAwayFrameHandler;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
      log.debug(String.format("%s - Received event for channelId: %s, event: %s", agentType, ctx.channel().id(),
        evt.getClass().getSimpleName()));
      super.userEventTriggered(ctx, evt);
    }

    @Override
    public synchronized void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      log.debug(String.format("%s - channelRead() called with msg type: %s", agentType, msg.getClass().getSimpleName()));

      if (msg instanceof DefaultHttp3SettingsFrame) {
        if (http3SettingsFrame == null) {
          http3SettingsFrame = (DefaultHttp3SettingsFrame) msg;
        }
        ReferenceCountUtil.release(msg);
      } else if (msg instanceof DefaultHttp3GoAwayFrame) {
        super.channelRead(ctx, msg);
        DefaultHttp3GoAwayFrame http3GoAwayFrame = (DefaultHttp3GoAwayFrame) msg;
        if (http3GoAwayFrameHandler != null) {
          http3GoAwayFrameHandler.handle(http3GoAwayFrame);
        }
        ReferenceCountUtil.release(msg);
      } else if (msg instanceof DefaultHttp3UnknownFrame) {
        if (log.isDebugEnabled()) {
          log.debug(String.format("%s - Received unknownFrame : %s", agentType, msg));
        }
        ReferenceCountUtil.release(msg);
        super.channelRead(ctx, msg);
      } else {
        super.channelRead(ctx, msg);
      }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
      log.debug(String.format("%s - ChannelReadComplete called for channelId: %s, streamId: %s", agentType,
        ctx.channel().id(), ((QuicStreamChannel) ctx.channel()).streamId()));

      synchronized (this) {
        if (http3SettingsFrame != null && !settingsRead) {
          settingsRead = true;

          if (http3SettingsFrameHandler != null) {
            http3SettingsFrameHandler.handle(http3SettingsFrame);
          }
        }
      }
      super.channelReadComplete(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      log.debug(String.format("%s - Caught exception on channelId : %s!", agentType, ctx.channel().id()), cause);
      super.exceptionCaught(ctx, cause);
    }

    public Http3ControlStreamChannelHandler agentType(String agentType) {
      this.agentType = agentType;
      return this;
    }

    public Http3ControlStreamChannelHandler http3SettingsFrameHandler(Handler<Http3SettingsFrame> http3SettingsFrameHandler) {
      this.http3SettingsFrameHandler = http3SettingsFrameHandler;
      return this;
    }

    public Http3ControlStreamChannelHandler http3GoAwayFrameHandler(Handler<Http3GoAwayFrame> http3GoAwayFrameHandler) {
      this.http3GoAwayFrameHandler = http3GoAwayFrameHandler;
      return this;
    }
  }

  public static class QuicSslHandlerWrapper extends SslHandler implements ChannelInboundHandler {
    private final ChannelDuplexHandler delegate;

    QuicSslHandlerWrapper(SSLEngine engine, Executor delegatedTaskExecutor, ChannelHandler quicSslHandler) {
      super(engine, delegatedTaskExecutor);
      delegate = (ChannelDuplexHandler) quicSslHandler;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext channelHandlerContext) throws Exception {
      delegate.channelRegistered(channelHandlerContext);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext channelHandlerContext) throws Exception {
      delegate.channelUnregistered(channelHandlerContext);
    }

    @Override
    public void channelActive(ChannelHandlerContext channelHandlerContext) throws Exception {
      delegate.channelActive(channelHandlerContext);
    }

    @Override
    public void channelInactive(ChannelHandlerContext channelHandlerContext) throws Exception {
      delegate.channelInactive(channelHandlerContext);
    }

    @Override
    public void channelRead(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {
      delegate.channelRead(channelHandlerContext, o);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext channelHandlerContext) throws Exception {
      delegate.channelReadComplete(channelHandlerContext);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {
      delegate.userEventTriggered(channelHandlerContext, o);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext channelHandlerContext) throws Exception {
      delegate.channelWritabilityChanged(channelHandlerContext);
    }

    @Override
    public void bind(ChannelHandlerContext channelHandlerContext, SocketAddress socketAddress, ChannelPromise channelPromise) throws Exception {
      delegate.bind(channelHandlerContext, socketAddress, channelPromise);
    }

    @Override
    public void connect(ChannelHandlerContext channelHandlerContext, SocketAddress socketAddress, SocketAddress socketAddress1, ChannelPromise channelPromise) throws Exception {
      delegate.connect(channelHandlerContext, socketAddress, socketAddress1, channelPromise);
    }

    @Override
    public void disconnect(ChannelHandlerContext channelHandlerContext, ChannelPromise channelPromise) throws Exception {
      delegate.disconnect(channelHandlerContext, channelPromise);
    }

    @Override
    public void close(ChannelHandlerContext channelHandlerContext, ChannelPromise channelPromise) throws Exception {
      delegate.close(channelHandlerContext, channelPromise);
    }

    @Override
    public void deregister(ChannelHandlerContext channelHandlerContext, ChannelPromise channelPromise) throws Exception {
      delegate.deregister(channelHandlerContext, channelPromise);
    }

    @Override
    public void read(ChannelHandlerContext channelHandlerContext) throws Exception {
      delegate.read(channelHandlerContext);
    }

    @Override
    public void write(ChannelHandlerContext channelHandlerContext, Object o, ChannelPromise channelPromise) throws Exception {
      delegate.write(channelHandlerContext, o, channelPromise);
    }

    @Override
    public void flush(ChannelHandlerContext channelHandlerContext) throws Exception {
      delegate.flush(channelHandlerContext);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext channelHandlerContext) throws Exception {
      delegate.handlerAdded(channelHandlerContext);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext channelHandlerContext, Throwable throwable) throws Exception {
      delegate.exceptionCaught(channelHandlerContext, throwable);
    }

    @Override
    public void handlerRemoved0(ChannelHandlerContext channelHandlerContext) throws Exception {
      delegate.handlerRemoved(channelHandlerContext);
    }
  }

  public interface QuicCodecBuilderInitializer {
    default void initServerCodecBuilder(QuicServerCodecBuilder quicServerCodecBuilder) {
      throw new RuntimeException("Not implemented");
    }
    default void initClientCodecBuilder(QuicClientCodecBuilder quicClientCodecBuilder) {
      throw new RuntimeException("Not implemented");
    }
  }
}
