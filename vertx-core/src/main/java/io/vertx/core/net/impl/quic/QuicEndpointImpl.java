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
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.quic.BoringSSLKeylog;
import io.netty.handler.codec.quic.FlushStrategy;
import io.netty.handler.codec.quic.QuicCodecBuilder;
import io.netty.util.internal.PlatformDependent;
import io.vertx.core.Closeable;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.quic.QuicEndpointInternal;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.ConnectionGroup;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.TransportMetrics;
import io.vertx.core.spi.tls.QuicSslContextFactory;
import io.vertx.core.spi.tls.SslContextFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class QuicEndpointImpl implements QuicEndpointInternal, MetricsProvider, Closeable {

  private static final EnumMap<QuicCongestionControlAlgorithm, io.netty.handler.codec.quic.QuicCongestionControlAlgorithm> CC_MAP = new EnumMap<>(QuicCongestionControlAlgorithm.class);

  static {
    CC_MAP.put(QuicCongestionControlAlgorithm.CUBIC, io.netty.handler.codec.quic.QuicCongestionControlAlgorithm.CUBIC);
    CC_MAP.put(QuicCongestionControlAlgorithm.RENO, io.netty.handler.codec.quic.QuicCongestionControlAlgorithm.RENO);
    CC_MAP.put(QuicCongestionControlAlgorithm.BBR, io.netty.handler.codec.quic.QuicCongestionControlAlgorithm.BBR);
  }

  private final QuicEndpointConfig config;
  private final SSLOptions sslOptions;
  protected final SslContextManager manager;
  protected final VertxInternal vertx;
  protected final TransportMetrics<?> metrics;
  private Channel channel;
  private SocketAddress actualLocalAddress;
  protected ConnectionGroup connectionGroup;
  private FlushStrategy flushStrategy;
  private ContextInternal context;

  public QuicEndpointImpl(VertxInternal vertx,
                          TransportMetrics<?> metricsProvider,
                          QuicEndpointConfig config,
                          SSLOptions sslOptions) {

    String keyLogFilePath = config.getKeyLogFile();
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

    this.config = config;
    this.sslOptions = sslOptions;
    this.vertx = Objects.requireNonNull(vertx);
    this.metrics = metricsProvider;
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

  protected abstract Future<QuicCodecBuilder<?>> codecBuilder(ContextInternal context, TransportMetrics<?> metrics) throws Exception;

  protected Future<ChannelHandler> channelHandler(ContextInternal context, SocketAddress bindAddr, TransportMetrics<?> metrics) throws Exception {
    return codecBuilder(context, metrics).map(codecBuilder -> {
      try {
        initQuicCodecBuilder(codecBuilder, metrics);
        return codecBuilder.build();
      } catch (Exception e) {
        // Improve this
        PlatformDependent.throwException(e);
        throw new AssertionError();
      }
    });
  }

  private Future<Channel> bind(ContextInternal context, SocketAddress bindAddr, TransportMetrics<?> metrics) {
   Bootstrap bootstrap = new Bootstrap()
      .group(context.nettyEventLoop())
      .channelFactory(vertx.transport().datagramChannelFactory());
   InetSocketAddress addr;
   if (bindAddr.hostAddress() != null) {
     addr = new InetSocketAddress(bindAddr.hostAddress(), bindAddr.port());
   } else {
     addr = new InetSocketAddress(bindAddr.hostName(), bindAddr.port());
   }
    Future<ChannelHandler> f;
    try {
      f = channelHandler(context, bindAddr, metrics);
    } catch (Exception e) {
      return context.failedFuture(e);
    }
    return f.compose(handler -> {
      bootstrap.handler(handler);
      ChannelFuture channelFuture = bootstrap.bind(addr);
      PromiseInternal<Void> p = context.promise();
      channelFuture.addListener(p);
      return p.future().map(v -> channelFuture.channel());
    });
  }

  void initQuicCodecBuilder(QuicCodecBuilder<?> codecBuilder, TransportMetrics<?> metrics) throws Exception {
    QuicConfig transportOptions = config.getTransportConfig();
    codecBuilder.initialMaxData(transportOptions.getInitialMaxData());
    codecBuilder.initialMaxStreamDataBidirectionalLocal(transportOptions.getInitialMaxStreamDataBidiLocal());
    codecBuilder.initialMaxStreamDataBidirectionalRemote(transportOptions.getInitialMaxStreamDataBidiRemote());
    codecBuilder.initialMaxStreamsBidirectional(transportOptions.getInitialMaxStreamsBidi());
    codecBuilder.initialMaxStreamsUnidirectional(transportOptions.getInitialMaxStreamsUni());
    codecBuilder.initialMaxStreamDataUnidirectional(transportOptions.getInitialMaxStreamDataUni());
    codecBuilder.activeMigration(!transportOptions.getDisableActiveMigration());
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
  }

  protected void handleBind(Channel channel, TransportMetrics<?> metrics) {
    this.channel = channel;
    this.connectionGroup = new ConnectionGroup(channel.eventLoop()) {
      @Override
      protected void handleClose(Completable<Void> completion) {
        PromiseInternal<Void> promise = (PromiseInternal<Void>) completion;
        Channel ch = channel;
        ch.close().addListener((ChannelFutureListener) future -> {
          if (metrics != null) {
            SocketAddress addr = actualLocalAddress;
            actualLocalAddress = null;
            if (addr != null) {
              metrics.unbound(false, addr);
            }
            metrics.close();
          }
          ContextInternal ctx;
          synchronized (QuicEndpointImpl.this) {
            ctx = context;
            context = null;
          }
          ctx.removeCloseHook(QuicEndpointImpl.this);
        }).addListener(promise);
      }
    };
  }

  @Override
  public Future<Integer> bind(SocketAddress address) {
    ContextInternal current = vertx.getOrCreateContext();
    synchronized (this) {
      if (context != null) {
        return current.failedFuture("Already bound");
      }
      context = current;
    }
    Future<SslContextProvider> f1 = manager.resolveSslContextProvider(sslOptions, current);
    return f1.compose(sslContextProvider -> bind(current, address, metrics)
      .map(ch -> {
        handleBind(ch, metrics);
        if (metrics != null) {
          actualLocalAddress = SocketAddress.inetSocketAddress(((InetSocketAddress) channel.localAddress()).getPort(), address.host());
          metrics.bound(false, actualLocalAddress);
        }
        context.addCloseHook(this);
        return ((InetSocketAddress)ch.localAddress()).getPort();
      }));
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    ConnectionGroup group = connectionGroup;
    if (group == null) {
      return vertx.getOrCreateContext().succeededFuture();
    } else {
      return group.shutdown(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
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

  @Override
  public void close(Completable<Void> completion) {
    close().onComplete(completion);
  }
}
