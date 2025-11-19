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
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.quic.QLogConfiguration;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicChannelBootstrap;
import io.netty.handler.codec.quic.QuicChannelOption;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.vertx.core.Future;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.TransportMetrics;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicClientImpl extends QuicEndpointImpl implements QuicClient {

  public static final QuicConnectOptions DEFAULT_CONNECT_OPTIONS = new QuicConnectOptions();
  private static final AttributeKey<SslContextProvider> SSL_CONTEXT_PROVIDER_KEY = AttributeKey.newInstance(SslContextProvider.class.getName());

  public static QuicClientImpl create(VertxInternal vertx, QuicClientOptions options) {
    return new QuicClientImpl(vertx, new QuicClientOptions(options));
  }

  private final QuicClientOptions options;
  private TransportMetrics<?> metrics;
  private volatile Channel channel;

  public QuicClientImpl(VertxInternal vertx, QuicClientOptions options) {
    super(vertx, options);
    this.options = options;
  }

  @Override
  protected void handleBind(Channel channel, TransportMetrics<?> metrics) {
    super.handleBind(channel, metrics);
    this.metrics = metrics;
    this.channel = channel;
  }

  @Override
  protected Future<QuicCodecBuilder<?>> codecBuilder(ContextInternal context, TransportMetrics<?> metrics) throws Exception {
    return context.succeededFuture(new QuicClientCodecBuilder()
      .sslEngineProvider(q -> {
        Attribute<SslContextProvider> attr = q.attr(SSL_CONTEXT_PROVIDER_KEY);
        SslContextProvider sslContextProvider = attr.get();
        QuicSslContext sslContext = (QuicSslContext) sslContextProvider.createContext(false, true);
        return sslContext.newEngine(q.alloc());
      })
      .maxIdleTimeout(5000, TimeUnit.MILLISECONDS));
  }

  @Override
  public Future<QuicConnection> connect(SocketAddress address) {
    return connect(address, new QuicConnectOptions());
  }

  @Override
  public Future<QuicConnection> connect(SocketAddress address, QuicConnectOptions connectOptions) {
    ContextInternal context = vertx.getOrCreateContext();
    ClientSSLOptions sslOptions = connectOptions.getSslOptions();
    if (sslOptions == null) {
      sslOptions = options.getSslOptions();
    }
    Future<SslContextProvider> fut = manager.resolveSslContextProvider(sslOptions, context);
    return fut.compose(sslContextProvider -> {
      Duration connectTimeout = connectOptions.getTimeout();
      if (connectTimeout == null) {
        connectTimeout = options.getConnectTimeout();
      }
      QLogConfig qlogConfig = connectOptions.getQLogConfig();
      if (qlogConfig == null) {
        qlogConfig = options.getQLogConfig();
      }
      return connect(address, qlogConfig, context, connectTimeout, sslContextProvider);
    });
  }

  private Future<QuicConnection> connect(SocketAddress address,
                                         QLogConfig qLogConfig,
                                         ContextInternal context,
                                         Duration connectTimeout,
                                         SslContextProvider sslContextProvider) {
    Channel ch = channel;
    if (ch == null) {
      return context.failedFuture("Client must be bound");
    }
    TransportMetrics<?> metrics = this.metrics;
    PromiseInternal<QuicConnection> promise = context.promise();
    QuicChannelBootstrap bootstrap = QuicChannel.newBootstrap(ch)
      .attr(SSL_CONTEXT_PROVIDER_KEY, sslContextProvider)
      .handler(new ChannelInitializer<>() {
        @Override
        protected void initChannel(Channel ch) {
          connectionGroup.add(ch);
          QuicConnectionHandler handler = new QuicConnectionHandler(context, metrics, options.getIdleTimeout(),
            options.getReadIdleTimeout(), options.getWriteIdleTimeout(), promise::tryComplete);
          ch.pipeline().addLast("handler", handler);
        }
      })
      .remoteAddress(new InetSocketAddress(address.host(), address.port()));
    if (qLogConfig != null) {
      bootstrap.option(QuicChannelOption.QLOG, new QLogConfiguration(qLogConfig.getPath(), qLogConfig.getTitle(), qLogConfig.getDescription()));
    }
    bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int)connectTimeout.toMillis());
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
