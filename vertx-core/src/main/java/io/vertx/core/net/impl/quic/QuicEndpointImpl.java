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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.quic.BoringSSLKeylog;
import io.netty.handler.codec.quic.FlushStrategy;
import io.netty.handler.codec.quic.QuicCodecBuilder;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.quic.QuicEndpointInternal;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ConnectionGroup;
import io.vertx.core.net.QuicCongestionControlAlgorithm;
import io.vertx.core.net.QuicEndpointOptions;
import io.vertx.core.net.QuicOptions;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.QuicEndpointMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.spi.tls.QuicSslContextFactory;
import io.vertx.core.spi.tls.SslContextFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class QuicEndpointImpl implements QuicEndpointInternal, MetricsProvider {

  private static final EnumMap<QuicCongestionControlAlgorithm, io.netty.handler.codec.quic.QuicCongestionControlAlgorithm> CC_MAP = new EnumMap<>(QuicCongestionControlAlgorithm.class);

  static {
    CC_MAP.put(QuicCongestionControlAlgorithm.CUBIC, io.netty.handler.codec.quic.QuicCongestionControlAlgorithm.CUBIC);
    CC_MAP.put(QuicCongestionControlAlgorithm.RENO, io.netty.handler.codec.quic.QuicCongestionControlAlgorithm.RENO);
    CC_MAP.put(QuicCongestionControlAlgorithm.BBR, io.netty.handler.codec.quic.QuicCongestionControlAlgorithm.BBR);
  }

  private final QuicEndpointOptions options;
  private final SslContextManager manager;
  protected final VertxInternal vertx;
  private QuicEndpointMetrics<?, ?> metrics;
  private Channel channel;
  protected ConnectionGroup connectionGroup;
  private FlushStrategy flushStrategy;

  public QuicEndpointImpl(VertxInternal vertx, QuicEndpointOptions options) {

    String keyLogFilePath = options.getKeyLogFile();
    File keylogFile;
    if (keyLogFilePath != null) {
      keylogFile = new File(keyLogFilePath);
      File parent;
      if (keylogFile.exists() && keylogFile.isFile()) {
        if (!keylogFile.isFile()) {
          keylogFile = null;
        }
      } else if ((parent = keylogFile.getParentFile()).exists() && parent.isDirectory()) {
        try {
          if (!keylogFile.createNewFile()) {
            keylogFile = null;
          }
        } catch (IOException ignore) {
          keylogFile = null;
        }
      }
    } else {
      keylogFile = null;
    }

    BoringSSLKeylog keylog;
    if (keylogFile != null) {
      keylog = new KeyLogFile(keylogFile);
    } else {
      keylog = null;
    }

    this.options = options;
    this.vertx = vertx;
    this.manager = new SslContextManager(new SSLEngineOptions() {
      @Override
      public SSLEngineOptions copy() {
        return this;
      }
      @Override
      public SslContextFactory sslContextFactory() {
        return new QuicSslContextFactory(keylog);
      }
    });
  }

  protected abstract QuicCodecBuilder<?> codecBuilder(ContextInternal context, SslContextProvider sslContextProvider, QuicEndpointMetrics<?, ?> metrics) throws Exception;

  protected abstract Future<SslContextProvider> createSslContextProvider(SslContextManager manager, ContextInternal context);

  protected ChannelHandler channelHandler(ContextInternal context, SocketAddress bindAddr, SslContextProvider sslContextProvider, QuicEndpointMetrics<?, ?> metrics) throws Exception {
    QuicCodecBuilder<?> codecBuilder = initQuicCodecBuilder(context, sslContextProvider, metrics);
    return codecBuilder.build();
  }

  private Future<Channel> bind(ContextInternal context, SocketAddress bindAddr, SslContextProvider sslContextProvider, QuicEndpointMetrics<?, ?> metrics) {
   Bootstrap bootstrap = new Bootstrap()
      .group(context.nettyEventLoop())
      .channelFactory(vertx.transport().datagramChannelFactory());
   InetSocketAddress addr;
   if (bindAddr.hostAddress() != null) {
     addr = new InetSocketAddress(bindAddr.hostAddress(), bindAddr.port());
   } else {
     addr = new InetSocketAddress(bindAddr.hostName(), bindAddr.port());
   }
    ChannelHandler handler;
    try {
      handler = channelHandler(context, bindAddr, sslContextProvider, metrics);
    } catch (Exception e) {
      return context.failedFuture(e);
    }
    bootstrap.handler(handler);
    ChannelFuture channelFuture = bootstrap.bind(addr);
    PromiseInternal<Void> p = context.promise();
    channelFuture.addListener(p);
    return p.future().map(v -> channelFuture.channel());
  }

  protected QuicCodecBuilder<?> initQuicCodecBuilder(ContextInternal context, SslContextProvider sslContextProvider, QuicEndpointMetrics<?, ?> metrics) throws Exception {
    QuicCodecBuilder<?> codecBuilder = codecBuilder(context, sslContextProvider, metrics);
    QuicOptions transportOptions = options.getTransportOptions();
    codecBuilder.initialMaxData(transportOptions.getInitialMaxData());
    codecBuilder.initialMaxStreamDataBidirectionalLocal(transportOptions.getInitialMaxStreamDataBidirectionalLocal());
    codecBuilder.initialMaxStreamDataBidirectionalRemote(transportOptions.getInitialMaxStreamDataBidirectionalRemote());
    codecBuilder.initialMaxStreamsBidirectional(transportOptions.getInitialMaxStreamsBidirectional());
    codecBuilder.initialMaxStreamsUnidirectional(transportOptions.getInitialMaxStreamsUnidirectional());
    codecBuilder.initialMaxStreamDataUnidirectional(transportOptions.getInitialMaxStreamDataUnidirectional());
    codecBuilder.activeMigration(transportOptions.getActiveMigration());
    if (transportOptions.getMaxIdleTimeout() != null) {
      codecBuilder.maxIdleTimeout(transportOptions.getMaxIdleTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }
    if (transportOptions.isEnableDatagrams()) {
      codecBuilder.datagram(transportOptions.getDatagramReceiveQueueLength(), transportOptions.getDatagramSendQueueLength());
    }
    codecBuilder.maxAckDelay(transportOptions.getMaxAckDelay().toMillis(), TimeUnit.MILLISECONDS);
    codecBuilder.ackDelayExponent(transportOptions.getAckDelayExponent());
    codecBuilder.congestionControlAlgorithm(CC_MAP.get(transportOptions.getCongestionControlAlgorithm()));
    FlushStrategy fStrategy = flushStrategy;
    if (fStrategy != null) {
      codecBuilder.flushStrategy(flushStrategy);
    }
    codecBuilder.grease(transportOptions.getGrease());
    codecBuilder.hystart(transportOptions.getHystart());
    codecBuilder.initialCongestionWindowPackets(transportOptions.getInitialCongestionWindowPackets());
    return codecBuilder;
  }

  protected void handleBind(Channel channel, QuicEndpointMetrics<?, ?> metrics) {
    this.channel = channel;
    this.metrics = metrics;
    this.connectionGroup = new ConnectionGroup(channel.eventLoop()) {
      @Override
      protected void handleClose(Completable<Void> completion) {
        PromiseInternal<Void> promise = (PromiseInternal<Void>) completion;
        Channel ch = channel;
        ch.close().addListener(promise);
      }
    };
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
  public Future<Void> shutdown(Duration timeout) {
    return connectionGroup.shutdown(timeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Override
  public Metrics getMetrics() {
    return metrics;
  }

  @Override
  public boolean isMetricsEnabled() {
    return metrics != null;
  }

  @Override
  public QuicEndpointInternal flushStrategy(FlushStrategy flushStrategy) {
    this.flushStrategy = flushStrategy;
    return this;
  }
}
