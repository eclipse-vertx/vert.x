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
package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;

import java.time.Duration;

import static io.vertx.core.net.NetServerOptions.*;

/**
 * Configuration of a {@link NetServer}
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class TcpServerConfig extends TcpEndpointConfig {

  private int port;
  private String host;
  private int acceptBacklog;
  private boolean useProxyProtocol;
  private Duration proxyProtocolTimeout;
  private TrafficShapingOptions trafficShapingOptions;

  public TcpServerConfig() {
    init();
  }

  public TcpServerConfig(TcpServerConfig other) {
    super(other);

    this.port = other.getPort();
    this.host = other.getHost();
    this.acceptBacklog = other.getAcceptBacklog();
    this.useProxyProtocol = other.isUseProxyProtocol();
    this.proxyProtocolTimeout = other.proxyProtocolTimeout;
    this.trafficShapingOptions = other.getTrafficShapingOptions() != null ? new TrafficShapingOptions(other.getTrafficShapingOptions()) : null;
  }

  public TcpServerConfig(NetServerOptions options) {
    super(options);

    this.port = options.getPort();
    this.host = options.getHost();
    this.acceptBacklog = options.getAcceptBacklog();
    this.useProxyProtocol = options.isUseProxyProtocol();
    this.proxyProtocolTimeout = Duration.of(options.getProxyProtocolTimeout(), options.getProxyProtocolTimeoutUnit().toChronoUnit());
    this.trafficShapingOptions = options.getTrafficShapingOptions() != null ? new TrafficShapingOptions(options.getTrafficShapingOptions()) : null;
  }


  private void init() {
    this.port = DEFAULT_PORT;
    this.host = DEFAULT_HOST;
    this.acceptBacklog = DEFAULT_ACCEPT_BACKLOG;
    this.useProxyProtocol = DEFAULT_USE_PROXY_PROTOCOL;
    this.proxyProtocolTimeout = Duration.of(DEFAULT_PROXY_PROTOCOL_TIMEOUT, DEFAULT_PROXY_PROTOCOL_TIMEOUT_TIME_UNIT.toChronoUnit());
    this.trafficShapingOptions = null;
  }

  public TcpServerConfig setTransportConfig(TcpConfig transportConfig) {
    return (TcpServerConfig)super.setTransportConfig(transportConfig);
  }

  public TcpServerConfig setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
    return (TcpServerConfig)super.setSslEngineOptions(sslEngineOptions);
  }

  public TcpServerConfig setIdleTimeout(Duration idleTimeout) {
    return (TcpServerConfig)super.setIdleTimeout(idleTimeout);
  }

  public TcpServerConfig setReadIdleTimeout(Duration idleTimeout) {
    return (TcpServerConfig)super.setReadIdleTimeout(idleTimeout);
  }

  public TcpServerConfig setWriteIdleTimeout(Duration idleTimeout) {
    return (TcpServerConfig)super.setWriteIdleTimeout(idleTimeout);
  }

  @Override
  public TcpServerConfig setNetworkLogging(NetworkLogging config) {
    return (TcpServerConfig)super.setNetworkLogging(config);
  }

  public TcpServerConfig setSsl(boolean ssl) {
    return (TcpServerConfig)super.setSsl(ssl);
  }

  /**
   * @return the port
   */
  public int getPort() {
    return port;
  }

  /**
   * Set the port
   *
   * @param port  the port
   * @return a reference to this, so the API can be used fluently
   */
  public TcpServerConfig setPort(int port) {
    if (port > 65535) {
      throw new IllegalArgumentException("port must be <= 65535");
    }
    this.port = port;
    return this;
  }

  /**
   *
   * @return the host
   */
  public String getHost() {
    return host;
  }

  /**
   * Set the host
   *
   * @param host  the host
   * @return a reference to this, so the API can be used fluently
   */
  public TcpServerConfig setHost(String host) {
    this.host = host;
    return this;
  }

  /**
   * @return the value of accept backlog
   */
  public int getAcceptBacklog() {
    return acceptBacklog;
  }

  /**
   * Set the accept back log
   *
   * @param acceptBacklog accept backlog
   * @return a reference to this, so the API can be used fluently
   */
  public TcpServerConfig setAcceptBacklog(int acceptBacklog) {
    this.acceptBacklog = acceptBacklog;
    return this;
  }

  /**
   * @return whether the server uses the HA Proxy protocol
   */
  public boolean isUseProxyProtocol() { return useProxyProtocol; }

  /**
   * Set whether the server uses the HA Proxy protocol
   *
   * @return a reference to this, so the API can be used fluently
   */
  public TcpServerConfig setUseProxyProtocol(boolean useProxyProtocol) {
    this.useProxyProtocol = useProxyProtocol;
    return this;
  }

  /**
   * @return the Proxy protocol timeout.
   */
  public Duration getProxyProtocolTimeout() {
    return proxyProtocolTimeout;
  }

  /**
   * Set the Proxy protocol timeout, default time unit.
   *
   * @param proxyProtocolTimeout the Proxy protocol timeout to set
   * @return a reference to this, so the API can be used fluently
   */
  public TcpServerConfig setProxyProtocolTimeout(Duration proxyProtocolTimeout) {
    if (proxyProtocolTimeout != null && proxyProtocolTimeout.isNegative()) {
      throw new IllegalArgumentException("proxyProtocolTimeout must be >= 0");
    }
    this.proxyProtocolTimeout = proxyProtocolTimeout;
    return this;
  }

  /**
   * @return traffic shaping options used by Net server.
   */
  public TrafficShapingOptions getTrafficShapingOptions() {
    return this.trafficShapingOptions;
  }

  /**
   * Set traffic shaping options. If not specified, traffic is unthrottled.
   *
   * @param trafficShapingOptions options used by traffic handler
   * @return a reference to this, so the API can be used fluently
   */
  public TcpServerConfig setTrafficShapingOptions(TrafficShapingOptions trafficShapingOptions) {
    this.trafficShapingOptions = trafficShapingOptions;
    return this;
  }
}
