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

import io.netty.channel.Channel;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.vertx.core.Future;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.quic.QuicClient;
import io.vertx.core.quic.QuicClientOptions;
import io.vertx.core.quic.QuicConnection;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicClientImpl extends QuicEndpointImpl implements QuicClient {

  public static QuicClientImpl create(VertxInternal vertx, QuicClientOptions options) {
    return new QuicClientImpl(vertx, new QuicClientOptions(options));
  }

  private final QuicClientOptions options;
  private volatile Channel channel;

  public QuicClientImpl(VertxInternal vertx, QuicClientOptions options) {
    super(vertx, options);
    this.options = options;
  }

  @Override
  protected void handleBind(Channel channel) {
    super.handleBind(channel);
    this.channel = channel;
  }

  @Override
  protected QuicCodecBuilder<?> codecBuilder(ContextInternal context, SslContextProvider sslContextProvider) {
    QuicSslContext sslContext = (QuicSslContext) sslContextProvider.createContext(false, true);
    return new QuicClientCodecBuilder()
      .sslContext(sslContext)
      .maxIdleTimeout(5000, TimeUnit.MILLISECONDS);
  }

  @Override
  protected Future<SslContextProvider> createSslContextProvider(SslContextManager manager, ContextInternal context) {
    return manager.resolveSslContextProvider(options.getSslOptions(), context);
  }

  @Override
  public Future<QuicConnection> connect(SocketAddress remotePeer) {
    ContextInternal context = vertx.getOrCreateContext();
    Channel ch = channel;
    if (ch == null) {
      return context.failedFuture("Client must be bound");
    }
    PromiseInternal<QuicConnection> promise = context.promise();
    QuicConnectionHandler handler = new QuicConnectionHandler(context, promise::tryComplete);
    io.netty.util.concurrent.Future<QuicChannel> res = QuicChannel.newBootstrap(ch)
      .handler(handler)
      .remoteAddress(new InetSocketAddress(remotePeer.host(), remotePeer.port()))
      .connect();
    res.addListener(future -> {
      if (!future.isSuccess()) {
        promise.tryFail(future.cause());
      }
    });
    return promise.future();
  }
}
