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
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.QuicEndpointMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.spi.tls.QuicSslContextFactory;
import io.vertx.core.spi.tls.SslContextFactory;

import java.net.InetSocketAddress;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class QuicEndpointImpl implements QuicEndpoint, MetricsProvider {

  private final QuicEndpointOptions options;
  private final SslContextManager manager;
  protected final VertxInternal vertx;
  private QuicEndpointMetrics<?, ?> metrics;
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

  protected abstract QuicCodecBuilder<?> codecBuilder(ContextInternal context, SslContextProvider sslContextProvider, QuicEndpointMetrics<?, ?> metrics);

  protected abstract Future<SslContextProvider> createSslContextProvider(SslContextManager manager, ContextInternal context);

  protected ChannelHandler channelHandler(ContextInternal context, SocketAddress bindAddr, SslContextProvider sslContextProvider, QuicEndpointMetrics<?, ?> metrics) {
    QuicCodecBuilder<?> codecBuilder = initQuicCodecBuilder(context, sslContextProvider, metrics);
    return codecBuilder.build();
  }

  private Future<Channel> bind(ContextInternal context, SocketAddress bindAddr, SslContextProvider sslContextProvider, QuicEndpointMetrics<?, ?> metrics) {
   Bootstrap bootstrap = new Bootstrap()
      .group(context.nettyEventLoop())
      .channelFactory(vertx.transport().datagramChannelFactory());
    InetSocketAddress addr = new InetSocketAddress(bindAddr.hostName(), bindAddr.port());
    ChannelHandler handler = channelHandler(context, bindAddr, sslContextProvider, metrics);
    bootstrap.handler(handler);
    ChannelFuture channelFuture = bootstrap.bind(addr);
    PromiseInternal<Void> p = context.promise();
    channelFuture.addListener(p);
    return p.future().map(v -> channelFuture.channel());
  }

  protected QuicCodecBuilder<?> initQuicCodecBuilder(ContextInternal context, SslContextProvider sslContextProvider, QuicEndpointMetrics<?, ?> metrics) {
    QuicCodecBuilder<?> codecBuilder = codecBuilder(context, sslContextProvider, metrics);
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
    return codecBuilder;
  }

  protected void handleBind(Channel channel, QuicEndpointMetrics<?, ?> metrics) {
    this.channel = channel;
    this.metrics = metrics;
  }

  @Override
  public Future<Void> bind(SocketAddress address) {
    ContextInternal context = vertx.getOrCreateContext();
    Future<SslContextProvider> f1 = createSslContextProvider(manager, context);
    return f1.compose(sslContextProvider -> {
      VertxMetrics metricsFactory = vertx.metrics();
      QuicEndpointMetrics<?, ?> metrics;
      if (metricsFactory != null) {
        metrics = metricsFactory.createQuicEndpointMetrics(options, address);
      } else {
        metrics = null;
      }
      return bind(context, address, sslContextProvider, metrics)
        .map(ch -> {
          handleBind(ch, metrics);
          return null;
        });
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

  @Override
  public Metrics getMetrics() {
    return metrics;
  }

  @Override
  public boolean isMetricsEnabled() {
    return metrics != null;
  }
}
