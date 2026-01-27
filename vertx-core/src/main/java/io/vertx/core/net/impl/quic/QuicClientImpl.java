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
import io.netty.handler.logging.ByteBufFormat;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.vertx.core.Future;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.resolver.NameResolver;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.TransportMetrics;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicClientImpl extends QuicEndpointImpl implements QuicClient {

  public static final QuicConnectOptions DEFAULT_CONNECT_OPTIONS = new QuicConnectOptions();
  private static final AttributeKey<SslContextProvider> SSL_CONTEXT_PROVIDER_KEY = AttributeKey.newInstance(SslContextProvider.class.getName());
  private static final AttributeKey<HostAndPort> SSL_SERVER_NAME_KEY = AttributeKey.newInstance(HostAndPort.class.getName());

  public static QuicClientImpl create(VertxInternal vertx, BiFunction<QuicEndpointConfig, SocketAddress, TransportMetrics<?>> metricsProvider, QuicClientConfig options) {
    return new QuicClientImpl(vertx, metricsProvider, new QuicClientConfig(options));
  }

  private final QuicClientConfig options;
  private TransportMetrics<?> metrics;
  private volatile Channel channel;

  public QuicClientImpl(VertxInternal vertx, BiFunction<QuicEndpointConfig, SocketAddress, TransportMetrics<?>> metricsProvider,
                        QuicClientConfig options) {
    super(vertx, metricsProvider, options);
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
    List<String> applicationProtocols = options.getSslOptions().getApplicationLayerProtocols();
    return context.succeededFuture(new QuicClientCodecBuilder()
      .sslEngineProvider(q -> {
        SslContextProvider sslContextProvider = q.attr(SSL_CONTEXT_PROVIDER_KEY).get();
        QuicSslContext sslContext = (QuicSslContext) sslContextProvider.createContext(false, applicationProtocols);
        if (q.hasAttr(SSL_SERVER_NAME_KEY)) {
          Attribute<HostAndPort> serverName = q.attr(SSL_SERVER_NAME_KEY);
          HostAndPort peer = serverName.get();
          return sslContext.newEngine(q.alloc(), peer.host(), peer.port());
        } else {
          return sslContext.newEngine(q.alloc());
        }
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
    String serverName = connectOptions.getServerName();
    return fut.compose(sslContextProvider -> {
      Duration connectTimeout = connectOptions.getTimeout();
      if (connectTimeout == null) {
        connectTimeout = options.getConnectTimeout();
      }
      QLogConfig qlogConfig = connectOptions.getQLogConfig();
      if (qlogConfig == null) {
        qlogConfig = options.getQLogConfig();
      }
      return connect(address, serverName, qlogConfig, context, connectTimeout, sslContextProvider);
    });
  }

  private Future<QuicConnection> connect(SocketAddress remoteAddress,
                                         String serverName,
                                         QLogConfig qLogConfig,
                                         ContextInternal context,
                                         Duration connectTimeout,
                                         SslContextProvider sslContextProvider) {
    NameResolver resolver = vertx.nameResolver();
    Future<java.net.SocketAddress> f = resolver.resolve(remoteAddress);
    return f.compose(res -> connect(remoteAddress, res, serverName, qLogConfig, context, connectTimeout, sslContextProvider));
  }

  private Future<QuicConnection> connect(SocketAddress remoteAddress,
                                         java.net.SocketAddress resolvedAddress,
                                         String serverName,
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
          ByteBufFormat activityLogging = options.getStreamLogging() != null ? options.getStreamLogging().getDataFormat() : null;
          QuicConnectionHandler handler = new QuicConnectionHandler(context, metrics, options.getStreamIdleTimeout(),
            options.getStreamReadIdleTimeout(), options.getStreamWriteIdleTimeout(), activityLogging, remoteAddress, promise::tryComplete);
          ch.pipeline().addLast("handler", handler);
        }
      })
      .remoteAddress(resolvedAddress);
    if (serverName != null && !serverName.isEmpty()) {
      bootstrap.attr(SSL_SERVER_NAME_KEY, HostAndPort.authority(serverName, remoteAddress.port()));
    }
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
