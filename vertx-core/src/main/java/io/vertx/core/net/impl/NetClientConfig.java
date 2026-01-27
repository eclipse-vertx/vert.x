/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl;

import io.netty.handler.logging.ByteBufFormat;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.tcp.NetEndpointConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration of a {@link NetClient}
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NetClientConfig extends NetEndpointConfig {

  private Duration connectTimeout;
  private String metricsName;
  private ProxyOptions proxyOptions;
  private List<String> nonProxyHosts;
  private int reconnectAttempts;
  private Duration reconnectInterval;

  public NetClientConfig() {
    super();
    this.connectTimeout = Duration.ofMillis(ClientOptionsBase.DEFAULT_CONNECT_TIMEOUT);
    this.metricsName = ClientOptionsBase.DEFAULT_METRICS_NAME;
    this.proxyOptions = null;
    this.nonProxyHosts = null;
    this.reconnectAttempts = NetClientOptions.DEFAULT_RECONNECT_ATTEMPTS;
    this.reconnectInterval = Duration.ofMillis(NetClientOptions.DEFAULT_RECONNECT_INTERVAL);
  }

  public NetClientConfig(NetClientConfig other) {
    super(other);
    this.connectTimeout = other.connectTimeout;
    this.metricsName = other.metricsName;
    this.proxyOptions = other.proxyOptions != null ? new ProxyOptions(other.proxyOptions) : null;
    this.nonProxyHosts = other.nonProxyHosts != null ? new ArrayList<>(other.nonProxyHosts) : null;
    this.reconnectAttempts = other.reconnectAttempts;
    this.reconnectInterval = other.reconnectInterval;
  }

  public NetClientConfig(NetClientOptions options) {
    super(options);
    setConnectTimeout(Duration.ofMillis(options.getConnectTimeout()));
    setMetricsName(options.getMetricsName());
    setNonProxyHosts(options.getNonProxyHosts() != null ? new ArrayList<>(options.getNonProxyHosts()) : null);
    setProxyOptions(options.getProxyOptions() != null ? new ProxyOptions(options.getProxyOptions()) : null);
    setReconnectAttempts(options.getReconnectAttempts());
    setReconnectInterval(Duration.ofMillis(options.getReconnectInterval()));
  }

  public NetClientConfig setTransportOptions(TcpOptions transportOptions) {
    return (NetClientConfig)super.setTransportOptions(transportOptions);
  }

  public ClientSSLOptions getSslOptions() {
    return (ClientSSLOptions)super.getSslOptions();
  }

  /**
   * Set the client SSL options.
   *
   * @param sslOptions the options
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientConfig setSslOptions(ClientSSLOptions sslOptions) {
    return (NetClientConfig)super.setSslOptions(sslOptions);
  }

  public NetClientConfig setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
    return (NetClientConfig)super.setSslEngineOptions(sslEngineOptions);
  }

  public NetClientConfig setIdleTimeout(Duration idleTimeout) {
    return (NetClientConfig)super.setIdleTimeout(idleTimeout);
  }

  public NetClientConfig setReadIdleTimeout(Duration idleTimeout) {
    return (NetClientConfig)super.setReadIdleTimeout(idleTimeout);
  }

  public NetClientConfig setWriteIdleTimeout(Duration idleTimeout) {
    return (NetClientConfig)super.setWriteIdleTimeout(idleTimeout);
  }

  public NetClientConfig setLogActivity(boolean logActivity) {
    return (NetClientConfig)super.setLogActivity(logActivity);
  }

  public NetClientConfig setActivityLogDataFormat(ByteBufFormat activityLogDataFormat) {
    return (NetClientConfig)super.setActivityLogDataFormat(activityLogDataFormat);
  }

  public NetClientConfig setSsl(boolean ssl) {
    return (NetClientConfig)super.setSsl(ssl);
  }

  /**
   * @return the value of connect timeout
   */
  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  /**
   * Set the connect timeout
   *
   * @param connectTimeout  connect timeout, in ms
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientConfig setConnectTimeout(Duration connectTimeout) {
    if (connectTimeout.isNegative() || connectTimeout.isZero()) {
      throw new IllegalArgumentException("connectTimeout must be >= 0");
    }
    this.connectTimeout = connectTimeout;
    return this;
  }

  /**
   * @return the metrics name identifying the reported metrics.
   */
  public String getMetricsName() {
    return metricsName;
  }

  /**
   * Set the metrics name identifying the reported metrics, useful for grouping metrics
   * with the same name.
   *
   * @param metricsName the metrics name
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientConfig setMetricsName(String metricsName) {
    this.metricsName = metricsName;
    return this;
  }

  /**
   * Get proxy options for connections
   *
   * @return proxy options
   */
  public ProxyOptions getProxyOptions() {
    return proxyOptions;
  }

  /**
   * Set proxy options for connections via CONNECT proxy (e.g. Squid) or a SOCKS proxy.
   *
   * @param proxyOptions proxy options object
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientConfig setProxyOptions(ProxyOptions proxyOptions) {
    this.proxyOptions = proxyOptions;
    return this;
  }

  /**
   * @return the list of non proxies hosts
   */
  public List<String> getNonProxyHosts() {
    return nonProxyHosts;
  }

  /**
   * Set a list of remote hosts that are not proxied when the client is configured to use a proxy. This
   * list serves the same purpose than the JVM {@code nonProxyHosts} configuration.
   *
   * <p> Entries can use the <i>*</i> wildcard character for pattern matching, e.g <i>*.example.com</i> matches
   * <i>www.example.com</i>.
   *
   * @param nonProxyHosts the list of non proxies hosts
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientConfig setNonProxyHosts(List<String> nonProxyHosts) {
    this.nonProxyHosts = nonProxyHosts;
    return this;
  }

  /**
   * Add a {@code host} to the {@link #getNonProxyHosts()} list.
   *
   * @param host the added host
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientConfig addNonProxyHost(String host) {
    if (nonProxyHosts == null) {
      nonProxyHosts = new ArrayList<>();
    }
    nonProxyHosts.add(host);
    return this;
  }

  /**
   * @return  the value of reconnect attempts
   */
  public int getReconnectAttempts() {
    return reconnectAttempts;
  }

  /**
   * Set the value of reconnect attempts
   *
   * @param attempts  the maximum number of reconnect attempts
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientConfig setReconnectAttempts(int attempts) {
    if (attempts < -1) {
      throw new IllegalArgumentException("reconnect attempts must be >= -1");
    }
    this.reconnectAttempts = attempts;
    return this;
  }

  /**
   * @return  the value of reconnect interval
   */
  public Duration getReconnectInterval() {
    return reconnectInterval;
  }

  /**
   * Set the reconnect interval
   *
   * @param interval  the reconnect interval
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientConfig setReconnectInterval(Duration interval) {
    if (interval.isNegative() || interval.isZero()) {
      throw new IllegalArgumentException("reconnect interval must be >= 1");
    }
    this.reconnectInterval = interval;
    return this;
  }
}
