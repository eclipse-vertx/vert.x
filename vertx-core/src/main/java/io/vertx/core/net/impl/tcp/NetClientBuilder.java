/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl.tcp;

import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.TcpOptions;
import io.vertx.core.net.impl.NetClientConfig;
import io.vertx.core.spi.metrics.TransportMetrics;

import java.time.Duration;
import java.util.ArrayList;

/**
 * A builder to configure NetClient plugins.
 */
public class NetClientBuilder {

  private VertxInternal vertx;
  private NetClientConfig config;
  private TransportMetrics metrics;
  private String localAddress;
  private boolean registerWriteHandler;

  public NetClientBuilder(VertxInternal vertx, NetClientConfig config) {
    this.vertx = vertx;
    this.config = config;
    this.localAddress = null;
    this.registerWriteHandler = false;
  }

  public NetClientBuilder(VertxInternal vertx, NetClientOptions options) {

    NetClientConfig cfg = new NetClientConfig();

    cfg.setConnectTimeout(Duration.ofMillis(options.getConnectTimeout()));
    cfg.setMetricsName(options.getMetricsName());
    cfg.setNonProxyHosts(options.getNonProxyHosts() != null ? new ArrayList<>(options.getNonProxyHosts()) : null);
    cfg.setProxyOptions(options.getProxyOptions() != null ? new ProxyOptions(options.getProxyOptions()) : null);
    cfg.setReconnectAttempts(options.getReconnectAttempts());
    cfg.setReconnectInterval(Duration.ofMillis(options.getReconnectInterval()));
    cfg.setSslOptions(options.getSslOptions() != null ? new ClientSSLOptions(options.getSslOptions()) : null);
    cfg.setTransportOptions(new TcpOptions(options.getTransportOptions()));
    cfg.setIdleTimeout(Duration.of(options.getIdleTimeout(), options.getIdleTimeoutUnit().toChronoUnit()));
    cfg.setReadIdleTimeout(Duration.of(options.getReadIdleTimeout(), options.getIdleTimeoutUnit().toChronoUnit()));
    cfg.setWriteIdleTimeout(Duration.of(options.getWriteIdleTimeout(), options.getIdleTimeoutUnit().toChronoUnit()));
    cfg.setSslEngineOptions(options.getSslEngineOptions() != null ? options.getSslEngineOptions().copy() : null);
    cfg.setLogActivity(options.getLogActivity());
    cfg.setActivityLogDataFormat(options.getActivityLogDataFormat());
    cfg.setSsl(options.isSsl());

    this.vertx = vertx;
    this.config = cfg;
    this.localAddress = options.getLocalAddress();
    this.registerWriteHandler = options.isRegisterWriteHandler();
  }

  public NetClientBuilder metrics(TransportMetrics metrics) {
    this.metrics = metrics;
    return this;
  }

  public NetClientInternal build() {
    return new NetClientImpl(vertx, metrics, config, registerWriteHandler, localAddress);
  }
}
