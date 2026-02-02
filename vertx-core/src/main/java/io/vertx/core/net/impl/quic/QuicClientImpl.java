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
import io.netty.util.NetUtil;
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
import java.util.Arrays;
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
  private static final AttributeKey<List<String>> APPLICATION_PROTOCOLS_KEY = AttributeKey.newInstance("io.vertx.net.quic.client.application_protocols");

  public static QuicClientImpl create(VertxInternal vertx,
                                      BiFunction<QuicEndpointConfig, SocketAddress, TransportMetrics<?>> metricsProvider,
                                      QuicClientConfig config, ClientSSLOptions sslOptions) {
    return new QuicClientImpl(vertx, metricsProvider, new QuicClientConfig(config), sslOptions);
  }

  private final QuicClientConfig config;
  private final ClientSSLOptions sslOptions;
  private TransportMetrics<?> metrics;
  private Future<Integer> clientFuture;
  private volatile Channel channel;

  public QuicClientImpl(VertxInternal vertx, BiFunction<QuicEndpointConfig, SocketAddress, TransportMetrics<?>> metricsProvider,
                        QuicClientConfig config, ClientSSLOptions sslOptions) {
    super(vertx, metricsProvider, config);
    this.config = config;
    this.sslOptions = sslOptions;
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
        SslContextProvider sslContextProvider = q.attr(SSL_CONTEXT_PROVIDER_KEY).get();
        Attribute<List<String>> applicationProtocols = q.attr(APPLICATION_PROTOCOLS_KEY);
        QuicSslContext sslContext = (QuicSslContext) sslContextProvider.createContext(false, applicationProtocols.get());
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
      sslOptions = this.sslOptions;
    } else {
      sslOptions = sslOptions.copy();
    }
    if (sslOptions == null) {
      return context.failedFuture("Missing client SSL options");
    }
    List<String> applicationProtocols = sslOptions.getApplicationLayerProtocols();
    if (applicationProtocols == null || applicationProtocols.isEmpty()) {
      return context.failedFuture(new IllegalArgumentException("Application protocols must be set on client SSL options"));
    }
    Future<SslContextProvider> fut = manager.resolveSslContextProvider(sslOptions, context);
    String serverName = connectOptions.getServerName();
    return fut.compose(sslContextProvider -> {
      Duration connectTimeout = connectOptions.getTimeout();
      if (connectTimeout == null) {
        connectTimeout = config.getConnectTimeout();
      }
      QLogConfig qlogConfig = connectOptions.getQLogConfig();
      if (qlogConfig == null) {
        qlogConfig = config.getQLogConfig();
      }
      return connect(address, serverName, qlogConfig, context, connectTimeout, applicationProtocols, sslContextProvider);
    });
  }

  private Future<QuicConnection> connect(SocketAddress remoteAddress,
                                         String serverName,
                                         QLogConfig qLogConfig,
                                         ContextInternal context,
                                         Duration connectTimeout,
                                         List<String> applicationProtocols,
                                         SslContextProvider sslContextProvider) {
    NameResolver resolver = vertx.nameResolver();
    Future<java.net.SocketAddress> f = resolver.resolve(remoteAddress);
    return f.compose(res -> connect(remoteAddress, res, serverName, qLogConfig, context, connectTimeout,
      applicationProtocols, sslContextProvider));
  }

  private Future<QuicConnection> connect(SocketAddress remoteAddress,
                                         java.net.SocketAddress resolvedAddress,
                                         String serverName,
                                         QLogConfig qLogConfig,
                                         ContextInternal context,
                                         Duration connectTimeout,
                                         List<String> applicationProtocols,
                                         SslContextProvider sslContextProvider) {
    Channel ch = channel;
    if (ch != null) {
      return connect(ch, remoteAddress, resolvedAddress, serverName, qLogConfig, context, connectTimeout,
        applicationProtocols, sslContextProvider);
    }
    Future<Integer> cf;
    synchronized (this) {
      cf = clientFuture;
      if (cf == null) {
        SocketAddress bindAddress = config.getLocalAddress();
        if (bindAddress == null) {
          bindAddress = SocketAddress.inetSocketAddress(0, NetUtil.LOCALHOST.getHostName());
        }
        cf = bind(bindAddress);
        clientFuture = cf;
      }
    }
    return cf
      .compose(port -> connect(channel, remoteAddress, resolvedAddress, serverName, qLogConfig, context,
        connectTimeout, applicationProtocols, sslContextProvider));
  }


  private Future<QuicConnection> connect(Channel ch,
                                         SocketAddress remoteAddress,
                                         java.net.SocketAddress resolvedAddress,
                                         String serverName,
                                         QLogConfig qLogConfig,
                                         ContextInternal context,
                                         Duration connectTimeout,
                                         List<String> applicationProtocols,
                                         SslContextProvider sslContextProvider) {
    TransportMetrics<?> metrics = this.metrics;
    PromiseInternal<QuicConnection> promise = context.promise();
    QuicChannelBootstrap bootstrap = QuicChannel.newBootstrap(ch)
      .attr(SSL_CONTEXT_PROVIDER_KEY, sslContextProvider)
      .attr(APPLICATION_PROTOCOLS_KEY, applicationProtocols)
      .handler(new ChannelInitializer<>() {
        @Override
        protected void initChannel(Channel ch) {
          connectionGroup.add(ch);
          ByteBufFormat activityLogging = config.getStreamLogging() != null ? config.getStreamLogging().getDataFormat() : null;
          QuicConnectionHandler handler = new QuicConnectionHandler(context, metrics, config.getStreamIdleTimeout(),
            config.getStreamReadIdleTimeout(), config.getStreamWriteIdleTimeout(), activityLogging, remoteAddress, promise::tryComplete);
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
