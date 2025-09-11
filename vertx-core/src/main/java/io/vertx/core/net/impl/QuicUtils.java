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
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3FrameToHttpObjectCodec;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicCodecBuilder;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicSslEngine;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.util.concurrent.Future;
import io.vertx.core.Handler;
import io.vertx.core.net.QuicOptions;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static io.vertx.core.net.QuicOptions.MAX_SSL_HANDSHAKE_TIMEOUT;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class QuicUtils {

  public static Http3FrameToHttpObjectCodec newClientFrameToHttpObjectCodec() {
    return new Http3FrameToHttpObjectCodec(false);
  }

  public static Http3FrameToHttpObjectCodec newServerFrameToHttpObjectCodec() {
    return new Http3FrameToHttpObjectCodec(true);
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

  public static ChannelHandler newClientSslContext(QuicOptions quicOptions, long sslHandshakeTimeout, TimeUnit sslHandshakeTimeoutUnit) {
    QuicSslContext context = QuicSslContextBuilder.forClient()
      .trustManager(InsecureTrustManagerFactory.INSTANCE)
      .applicationProtocols(Http3.supportedApplicationProtocols()).build();

    quicOptions.setHttp3InitialMaxData(10000000);

    return configureQuicCodecBuilder(Http3.newQuicClientCodecBuilder().sslContext(context), quicOptions, sslHandshakeTimeout, sslHandshakeTimeoutUnit).build();
  }

  public static ServerQuicCodecBuilderInitializer createServerQuicCodecBuilderInitializer(QuicOptions quicOptions, ChannelHandler handler, long sslHandshakeTimeout, TimeUnit sslHandshakeTimeoutUnit) {
    return quicServerCodecBuilder -> configureQuicCodecBuilder(quicServerCodecBuilder, quicOptions, sslHandshakeTimeout, sslHandshakeTimeoutUnit)
      .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
      .handler(handler);
  }

  public static ClientQuicCodecBuilderInitializer createClientQuicCodecBuilderInitializer(QuicOptions quicOptions, long sslHandshakeTimeout, TimeUnit sslHandshakeTimeoutUnit) {
    return quicClientCodecBuilder -> configureQuicCodecBuilder(quicClientCodecBuilder, quicOptions, sslHandshakeTimeout, sslHandshakeTimeoutUnit);
  }

  public static SslHandlerWrapper newQuicServerSslHandler(QuicSslEngine sslEngine, Executor delegatedTaskExecutor, SslContext sslContext, ServerQuicCodecBuilderInitializer initializer) {
    ChannelHandler handler = newQuicServerHandler(delegatedTaskExecutor, (QuicSslContext) sslContext, quicChannel -> sslEngine, initializer).build();
    return new SslHandlerWrapper(sslEngine, delegatedTaskExecutor, handler);
  }

  private static QuicServerCodecBuilder newQuicServerHandler(Executor delegatedTaskExecutor, QuicSslContext sslContext, Function<QuicChannel, ? extends QuicSslEngine> sslEngineProvider, ServerQuicCodecBuilderInitializer initializer) {
    QuicServerCodecBuilder quicCodecBuilder = Http3.newQuicServerCodecBuilder();
    initializer.initServerCodecBuilder(quicCodecBuilder);
    return quicCodecBuilder
      .sslTaskExecutor(delegatedTaskExecutor)
      .sslContext(sslContext)
      .sslEngineProvider(sslEngineProvider);
  }

  public static SslHandlerWrapper newQuicClientSslHandler(QuicSslEngine engine, Executor delegatedTaskExecutor, SslContext sslContext, ClientQuicCodecBuilderInitializer initializer) {
    QuicClientCodecBuilder quicCodecBuilder = Http3.newQuicClientCodecBuilder();
    initializer.initClientCodecBuilder(quicCodecBuilder);
    ChannelHandler handler = quicCodecBuilder
      .sslTaskExecutor(delegatedTaskExecutor)
      .sslContext((QuicSslContext) sslContext)
      .sslEngineProvider(quicChannel -> engine)
      .build();
    return new SslHandlerWrapper(engine, delegatedTaskExecutor, handler);
  }

  public static <T extends QuicCodecBuilder<T>> T configureQuicCodecBuilder(T quicCodecBuilder, QuicOptions quicOptions, long sslHandshakeTimeout, TimeUnit sslHandshakeTimeoutUnit) {
    if (Duration.of(sslHandshakeTimeout, sslHandshakeTimeoutUnit.toChronoUnit()).compareTo(MAX_SSL_HANDSHAKE_TIMEOUT) > 0) {
      // Very large values can trigger crashes in lower-level Rust code
      throw new IllegalArgumentException("sslHandshakeTimeout must be ≤ " + MAX_SSL_HANDSHAKE_TIMEOUT);
    }

    quicCodecBuilder
      // Enabling this option allows sending unreliable, connectionless data over QUIC
      // via QUIC datagrams. It is required for VertxHandler and net socket to function properly.
      .datagram(2000000, 2000000)

      .maxIdleTimeout(sslHandshakeTimeout, sslHandshakeTimeoutUnit)
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

  public interface ServerQuicCodecBuilderInitializer {
    void initServerCodecBuilder(QuicServerCodecBuilder quicServerCodecBuilder);
  }

  public interface ClientQuicCodecBuilderInitializer {
    void initClientCodecBuilder(QuicClientCodecBuilder quicClientCodecBuilder);
  }
}
