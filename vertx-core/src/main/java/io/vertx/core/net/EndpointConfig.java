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
package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration of an endpoint.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public abstract class EndpointConfig {

  private TransportConfig transportConfig;
  private Duration idleTimeout;
  private Duration readIdleTimeout;
  private Duration writeIdleTimeout;
  private String metricsName;
  private NetworkLogging networkLogging;

  EndpointConfig() {
    this.transportConfig = null;
    this.idleTimeout = null;
    this.readIdleTimeout = null;
    this.writeIdleTimeout = null;
    this.metricsName = null;
    this.networkLogging = null;
  }

  public EndpointConfig(EndpointConfig other) {
    this.transportConfig = other.transportConfig.copy();
    this.idleTimeout = other.idleTimeout;
    this.readIdleTimeout = other.readIdleTimeout;
    this.writeIdleTimeout = other.writeIdleTimeout;
    this.metricsName = other.metricsName;
    this.networkLogging = other.networkLogging != null ? new NetworkLogging(other.networkLogging) : null;
  }

  /**
   * @return the endpoint transport config
   */
  public TransportConfig getTransportConfig() {
    return transportConfig;
  }

  EndpointConfig setTransportConfig(TransportConfig transportConfig) {
    this.transportConfig = Objects.requireNonNull(transportConfig);
    return this;
  }

  /**
   * Set the stream idle timeout, zero or {@code null} means don't time out.
   * This determines if a stream will timeout and be closed if no data is received nor sent within the timeout.
   *
   * @param idleTimeout  the idle timeout
   * @return a reference to this, so the API can be used fluently
   */
  public EndpointConfig setIdleTimeout(Duration idleTimeout) {
    if (idleTimeout != null && idleTimeout.isNegative()) {
      throw new IllegalArgumentException("streamIdleTimeout must be >= 0");
    }
    this.idleTimeout = idleTimeout;
    return this;
  }

  /**
   * @return the idle timeout applied to each stream
   */
  public Duration getIdleTimeout() {
    return idleTimeout;
  }

  /**
   * <p>Set the stream read idle timeout, zero or {@code null} means or null means don't time out. This determines if a
   * stream will timeout and be closed if no data is received within the timeout.</p>
   *
   * @param idleTimeout  the read idle timeout
   * @return a reference to this, so the API can be used fluently
   */
  public EndpointConfig setReadIdleTimeout(Duration idleTimeout) {
    if (idleTimeout != null && idleTimeout.isNegative()) {
      throw new IllegalArgumentException("streamReadIdleTimeout must be >= 0");
    }
    this.readIdleTimeout = idleTimeout;
    return this;
  }

  /**
   * @return the read idle timeout applied to each stream
   */
  public Duration getReadIdleTimeout() {
    return readIdleTimeout;
  }

  /**
   * <p>Set the stream write idle timeout, zero or {@code null} means don't time out. This determines if a
   * stream will timeout and be closed if no data is sent within the timeout.</p>
   *
   * @param idleTimeout  the write idle timeout
   * @return a reference to this, so the API can be used fluently
   */
  public EndpointConfig setWriteIdleTimeout(Duration idleTimeout) {
    if (idleTimeout != null && idleTimeout.isNegative()) {
      throw new IllegalArgumentException("streamWriteIdleTimeout must be >= 0");
    }
    this.writeIdleTimeout = idleTimeout;
    return this;
  }

  /**
   * @return the write idle timeout applied to each stream
   */
  public Duration getWriteIdleTimeout() {
    return writeIdleTimeout;
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
  public EndpointConfig setMetricsName(String metricsName) {
    this.metricsName = metricsName;
    return this;
  }

  /**
   * @return the stream network logging config, {@code null} means disabled
   */
  public NetworkLogging getNetworkLogging() {
    return networkLogging;
  }

  /**
   * Configure the per stream networking logging: Netty's stream pipeline is configured for logging on Netty's logger.
   *
   * @param config the stream network logging config, {@code null} means disabled
   * @return a reference to this, so the API can be used fluently
   */
  public EndpointConfig setNetworkLogging(NetworkLogging config) {
    this.networkLogging = config;
    return this;
  }
}
