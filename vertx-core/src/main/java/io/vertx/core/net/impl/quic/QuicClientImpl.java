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
package io.vertx.core.net.impl.quic;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.quic.QLogConfiguration;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicChannelBootstrap;
import io.netty.handler.codec.quic.QuicChannelOption;
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
import io.vertx.core.net.QLogConfig;
import io.vertx.core.net.QuicClient;
import io.vertx.core.net.QuicClientOptions;
import io.vertx.core.net.QuicConnection;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.QuicEndpointMetrics;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicClientImpl extends QuicEndpointImpl implements QuicClient {

  public static QuicClientImpl create(VertxInternal vertx, QuicClientOptions options) {
    return new QuicClientImpl(vertx, new QuicClientOptions(options));
  }

  private final QuicClientOptions options;
  private QuicEndpointMetrics<?, ?> metrics;
  private volatile Channel channel;

  public QuicClientImpl(VertxInternal vertx, QuicClientOptions options) {
    super(vertx, options);
    this.options = options;
  }

  @Override
  protected void handleBind(Channel channel, QuicEndpointMetrics<?, ?> metrics) {
    super.handleBind(channel, metrics);
    this.metrics = metrics;
    this.channel = channel;
  }

  @Override
  protected QuicCodecBuilder<?> codecBuilder(ContextInternal context, SslContextProvider sslContextProvider, QuicEndpointMetrics<?, ?> metrics) {
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
  public Future<QuicConnection> connect(SocketAddress address) {
    return connect(address, options.getQLogConfig());
  }

  @Override
  public Future<QuicConnection> connect(SocketAddress address, QLogConfig qLogConfig) {
    ContextInternal context = vertx.getOrCreateContext();
    Channel ch = channel;
    if (ch == null) {
      return context.failedFuture("Client must be bound");
    }
    QuicEndpointMetrics<?, ?> metrics = this.metrics;
    PromiseInternal<QuicConnection> promise = context.promise();
    QuicChannelBootstrap bootstrap = QuicChannel.newBootstrap(ch)
      .handler(new ChannelInitializer<>() {
        @Override
        protected void initChannel(Channel ch) {
          connectionGroup.add(ch);
          QuicConnectionHandler handler = new QuicConnectionHandler(context, metrics, promise::tryComplete);
          ch.pipeline().addLast("handler", handler);
        }
      })
      .remoteAddress(new InetSocketAddress(address.host(), address.port()));
    if (qLogConfig != null) {
      bootstrap.option(QuicChannelOption.QLOG, new QLogConfiguration(qLogConfig.getPath(), qLogConfig.getTitle(), qLogConfig.getDescription()));
    }
    io.netty.util.concurrent.Future<QuicChannel> res = bootstrap
      .connect();
    res.addListener(future -> {
      if (!future.isSuccess()) {
        promise.tryFail(future.cause());
      }
    });
    return promise.future();
  }

  @Override
  public Metrics getMetrics() {
    return metrics;
  }
}
