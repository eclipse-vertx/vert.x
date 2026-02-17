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

import static io.vertx.core.net.ClientOptionsBase.DEFAULT_METRICS_NAME;

/**
 * Should this be {@code TcpConfig} instead ?
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public abstract class TcpEndpointConfig extends EndpointConfig {

  private SSLEngineOptions sslEngineOptions;
  private boolean ssl;

  public TcpEndpointConfig() {
    super();
    setTransportConfig(new TcpConfig());
    this.sslEngineOptions = TCPSSLOptions.DEFAULT_SSL_ENGINE;
    this.ssl = TCPSSLOptions.DEFAULT_SSL;
  }

  public TcpEndpointConfig(TcpEndpointConfig other) {
    super(other);
    this.sslEngineOptions = other.sslEngineOptions != null ? other.sslEngineOptions.copy() : null;
    this.ssl = other.ssl;
  }

  public TcpEndpointConfig(TCPSSLOptions options) {
    super();
    setTransportConfig(new TcpConfig(options.getTransportOptions()));
    setSslEngineOptions(options.getSslEngineOptions() != null ? options.getSslEngineOptions().copy() : null);
    setIdleTimeout(Duration.of(options.getIdleTimeout(), options.getIdleTimeoutUnit().toChronoUnit()));
    setReadIdleTimeout(Duration.of(options.getReadIdleTimeout(), options.getIdleTimeoutUnit().toChronoUnit()));
    setWriteIdleTimeout(Duration.of(options.getWriteIdleTimeout(), options.getIdleTimeoutUnit().toChronoUnit()));
    setNetworkLogging(options.getLogActivity() ? new NetworkLogging().setDataFormat(options.getActivityLogDataFormat()) : null);
    setSsl(options.isSsl());
  }

  /**
   * @return the client TCP transport options
   */
  public TcpConfig getTransportConfig() {
    return (TcpConfig) super.getTransportConfig();
  }

  /**
   * Set the client TCP transport config.
   *
   * @param transportConfig the transport config
   * @return a reference to this, so the API can be used fluently
   */
  public TcpEndpointConfig setTransportConfig(TcpConfig transportConfig) {
    return (TcpEndpointConfig) super.setTransportConfig(transportConfig);
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
  public TcpEndpointConfig setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
    this.sslEngineOptions = sslEngineOptions;
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
  public TcpEndpointConfig setSsl(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  public TcpEndpointConfig setIdleTimeout(Duration idleTimeout) {
    return (TcpEndpointConfig) super.setIdleTimeout(idleTimeout);
  }

  public TcpEndpointConfig setReadIdleTimeout(Duration idleTimeout) {
    return (TcpEndpointConfig) super.setReadIdleTimeout(idleTimeout);
  }

  public TcpEndpointConfig setWriteIdleTimeout(Duration idleTimeout) {
    return (TcpEndpointConfig) super.setWriteIdleTimeout(idleTimeout);
  }

  public TcpEndpointConfig setMetricsName(String metricsName) {
    return (TcpEndpointConfig) super.setMetricsName(metricsName);
  }

  public TcpEndpointConfig setNetworkLogging(NetworkLogging config) {
    return (TcpEndpointConfig) super.setNetworkLogging(config);
  }
}
