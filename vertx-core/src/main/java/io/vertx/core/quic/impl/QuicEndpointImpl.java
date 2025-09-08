/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.quic.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.QuicCodecBuilder;
import io.vertx.core.Future;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.quic.QuicEndpoint;
import io.vertx.core.quic.QuicEndpointOptions;
import io.vertx.core.quic.QuicOptions;
import io.vertx.core.spi.tls.QuicSslContextFactory;
import io.vertx.core.spi.tls.SslContextFactory;

import java.net.InetSocketAddress;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class QuicEndpointImpl implements QuicEndpoint {

  private final QuicEndpointOptions options;
  private final SslContextManager manager;
  protected final VertxInternal vertx;
  private Channel channel;

  public QuicEndpointImpl(VertxInternal vertx, QuicEndpointOptions options) {
    this.options = options;
    this.vertx = vertx;
    this.manager = new SslContextManager(new SSLEngineOptions() {
      @Override
      public SSLEngineOptions copy() {
        return this;
      }
      @Override
      public SslContextFactory sslContextFactory() {
        return new QuicSslContextFactory();
      }
    });
  }

  protected abstract QuicCodecBuilder<?> codecBuilder(ContextInternal context, SslContextProvider sslContextProvider);

  protected abstract Future<SslContextProvider> createSslContextProvider(SslContextManager manager, ContextInternal context);

  private Future<Channel> bind(ContextInternal context, SocketAddress bindAddr, SslContextProvider sslContextProvider) {
    InetSocketAddress addr = new InetSocketAddress(bindAddr.hostName(), bindAddr.port());
    QuicCodecBuilder<?> codecBuilder = codecBuilder(context, sslContextProvider);
    QuicOptions transportOptions = options.getTransportOptions();
    if (transportOptions.getInitialMaxData() != null) {
      codecBuilder.initialMaxData(transportOptions.getInitialMaxData());
    }
    if (transportOptions.getInitialMaxStreamDataBidirectionalRemote() != null) {
      codecBuilder.initialMaxStreamDataBidirectionalLocal(transportOptions.getInitialMaxStreamDataBidirectionalRemote());
    }
    if (transportOptions.getInitialMaxStreamDataBidirectionalRemote() != null) {
      codecBuilder.initialMaxStreamDataBidirectionalRemote(transportOptions.getInitialMaxStreamDataBidirectionalRemote());
    }
    if (transportOptions.getInitialMaxStreamsBidirectional() != null) {
      codecBuilder.initialMaxStreamsBidirectional(transportOptions.getInitialMaxStreamsBidirectional());
    }
    if (transportOptions.getInitialMaxStreamsUnidirectional() != null) {
      codecBuilder.initialMaxStreamsUnidirectional(transportOptions.getInitialMaxStreamsUnidirectional());
    }
    if (transportOptions.getInitialMaxStreamDataBidirectionalLocal() != null) {
      codecBuilder.initialMaxStreamDataBidirectionalLocal(transportOptions.getInitialMaxStreamDataBidirectionalLocal());
    }
    if (transportOptions.getInitialMaxStreamDataUnidirectional() != null) {
      codecBuilder.initialMaxStreamDataUnidirectional(transportOptions.getInitialMaxStreamDataUnidirectional());
    }
    if (transportOptions.getActiveMigration() != null) {
      codecBuilder.activeMigration(transportOptions.getActiveMigration());
    }

    ChannelHandler codec = codecBuilder.build();
    Bootstrap bs = new Bootstrap();
    ChannelFuture channelFuture = bs.group(context.nettyEventLoop())
      .channelFactory(vertx.transport().datagramChannelFactory())
      .handler(codec)
      .bind(addr);
    PromiseInternal<Void> p = context.promise();
    channelFuture.addListener(p);
    return p.future().map(v -> channelFuture.channel());
  }

  protected void handleBind(Channel channel) {
    this.channel = channel;
  }

  @Override
  public Future<Void> bind(SocketAddress address) {
    ContextInternal context = vertx.getOrCreateContext();
    Future<SslContextProvider> f1 = createSslContextProvider(manager, context);
    Future<Channel> f2 = f1.compose(sslContextProvider -> bind(context, address, sslContextProvider));
    return f2.map(ch -> {
      handleBind(ch);
      return null;
    });
  }

  @Override
  public Future<Void> close() {
    ContextInternal context = vertx.getOrCreateContext();
    Channel ch = channel;
    if (ch == null) {
      return context.succeededFuture();
    }
    PromiseInternal<Void> promise = context.promise();
    ch.close().addListener(promise);
    return promise.future();
  }
}
