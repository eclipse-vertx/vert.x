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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration of a {@link NetClient}
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NetClientConfig {

  private TcpOptions transportOptions;
  private ClientSSLOptions sslOptions;
  private SSLEngineOptions sslEngineOptions;
  private Duration connectTimeout;
  private String metricsName;
  private ProxyOptions proxyOptions;
  private List<String> nonProxyHosts;
  private int reconnectAttempts;
  private Duration reconnectInterval;
  private Duration idleTimeout;
  private Duration readIdleTimeout;
  private Duration writeIdleTimeout;
  private boolean logActivity;
  private ByteBufFormat activityLogDataFormat;
  private boolean ssl;

  public NetClientConfig() {
    this.transportOptions = new TcpOptions();
    this.sslOptions = null;
    this.sslEngineOptions = TCPSSLOptions.DEFAULT_SSL_ENGINE;
    this.connectTimeout = Duration.ofMillis(ClientOptionsBase.DEFAULT_CONNECT_TIMEOUT);
    this.metricsName = ClientOptionsBase.DEFAULT_METRICS_NAME;
    this.proxyOptions = null;
    this.nonProxyHosts = null;
    this.reconnectAttempts = NetClientOptions.DEFAULT_RECONNECT_ATTEMPTS;
    this.reconnectInterval = Duration.ofMillis(NetClientOptions.DEFAULT_RECONNECT_INTERVAL);
    this.logActivity = NetworkOptions.DEFAULT_LOG_ENABLED;
    this.activityLogDataFormat = NetworkOptions.DEFAULT_LOG_ACTIVITY_FORMAT;
    this.ssl = TCPSSLOptions.DEFAULT_SSL;
  }

  public NetClientConfig(NetClientConfig other) {
    this.transportOptions = other.transportOptions != null ? new TcpOptions(other.transportOptions) : null;
    this.sslOptions = other.sslOptions != null ? new ClientSSLOptions(other.sslOptions) : null;
    this.sslEngineOptions = other.sslEngineOptions != null ? other.sslEngineOptions.copy() : null;
    this.connectTimeout = other.connectTimeout;
    this.metricsName = other.metricsName;
    this.proxyOptions = other.proxyOptions != null ? new ProxyOptions(other.proxyOptions) : null;
    this.nonProxyHosts = other.nonProxyHosts != null ? new ArrayList<>(other.nonProxyHosts) : null;
    this.reconnectAttempts = other.reconnectAttempts;
    this.reconnectInterval = other.reconnectInterval;
    this.logActivity = other.logActivity;
    this.activityLogDataFormat = other.activityLogDataFormat;
    this.ssl = other.ssl;
  }

  /**
   * @return the client TCP transport options
   */
  public TcpOptions getTransportOptions() {
    return transportOptions;
  }

  /**
   * Set the client TCP transport options.
   *
   * @param transportOptions the transport options
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientConfig setTransportOptions(TcpOptions transportOptions) {
    this.transportOptions = transportOptions;
    return this;
  }

  /**
   * @return the client SSL options.
   */
  public ClientSSLOptions getSslOptions() {
    return sslOptions;
  }

  /**
   * Set the client SSL options.
   *
   * @param sslOptions the options
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientConfig setSslOptions(ClientSSLOptions sslOptions) {
    this.sslOptions = sslOptions;
    return this;
  }

  /**
   * @return the SSL engine implementation to use
   */
  public SSLEngineOptions getSslEngineOptions() {
    return sslEngineOptions;
  }

  /**
   * Set to use SSL engine implementation to use.
   *
   * @param sslEngineOptions the ssl engine to use
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientConfig setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
    this.sslEngineOptions = sslEngineOptions;
    return this;
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

  /**
   * @return the idle timeout
   */
  public Duration getIdleTimeout() {
    return idleTimeout;
  }

  /**
   * Set the idle timeout, default time unit is seconds. Zero means don't time out.
   * This determines if a connection will timeout and be closed if no data is received nor sent within the timeout.
   *
   * @param idleTimeout  the timeout
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientConfig setIdleTimeout(Duration idleTimeout) {
    if (idleTimeout != null && idleTimeout.isNegative()) {
      throw new IllegalArgumentException("idleTimeout must be >= 0");
    }
    this.idleTimeout = idleTimeout;
    return this;
  }

  /**
   * Set the read idle timeout. Zero means don't time out.
   * This determines if a connection will timeout and be closed if no data is received within the timeout.
   *
   * @param idleTimeout  the read timeout
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientConfig setReadIdleTimeout(Duration idleTimeout) {
    if (idleTimeout != null && idleTimeout.isNegative()) {
      throw new IllegalArgumentException("readIdleTimeout must be >= 0");
    }
    this.readIdleTimeout = idleTimeout;
    return this;
  }

  /**
   * @return the read idle timeout
   */
  public Duration getReadIdleTimeout() {
    return readIdleTimeout;
  }

  /**
   * Set the write idle timeout, default time unit is seconds. Zero means don't time out.
   * This determines if a connection will timeout and be closed if no data is sent within the timeout.
   *
   * @param idleTimeout  the write timeout
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientConfig setWriteIdleTimeout(Duration idleTimeout) {
    if (idleTimeout != null && idleTimeout.isNegative()) {
      throw new IllegalArgumentException("writeIdleTimeout must be >= 0");
    }
    this.writeIdleTimeout = idleTimeout;
    return this;
  }

  /**
   * @return the write idle timeout.
   */
  public Duration getWriteIdleTimeout() {
    return writeIdleTimeout;
  }

  /**
   * @return true when network activity logging is enabled
   */
  public boolean getLogActivity() {
    return logActivity;
  }

  /**
   * Set to true to enabled network activity logging: Netty's pipeline is configured for logging on Netty's logger.
   *
   * @param logActivity true for logging the network activity
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientConfig setLogActivity(boolean logActivity) {
    this.logActivity = logActivity;
    return this;
  }

  /**
   * @return Netty's logging handler's data format.
   */
  public ByteBufFormat getActivityLogDataFormat() {
    return activityLogDataFormat;
  }

  /**
   * Set the value of Netty's logging handler's data format: Netty's pipeline is configured for logging on Netty's logger.
   *
   * @param activityLogDataFormat the format to use
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientConfig setActivityLogDataFormat(ByteBufFormat activityLogDataFormat) {
    this.activityLogDataFormat = activityLogDataFormat;
    return this;
  }

  /**
   *
   * @return is SSL/TLS enabled?
   */
  public boolean isSsl() {
    return ssl;
  }

  /**
   * Set whether SSL/TLS is enabled
   *
   * @param ssl  true if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientConfig setSsl(boolean ssl) {
    this.ssl = ssl;
    return this;
  }
}
