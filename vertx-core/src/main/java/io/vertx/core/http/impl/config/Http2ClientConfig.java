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
package io.vertx.core.http.impl.config;

import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpClientOptions;

import java.time.Duration;

/**
 * HTTP/2 client configuration.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ClientConfig {

  private int multiplexingLimit;
  private int connectionWindowSize;
  private Duration keepAliveTimeout;
  private int upgradeMaxContentLength;
  private boolean multiplexImplementation;
  private Http2Settings initialSettings;
  private boolean clearTextUpgrade;
  private boolean clearTextUpgradeWithPreflightRequest;

  public Http2ClientConfig() {
    multiplexingLimit = HttpClientOptions.DEFAULT_HTTP2_MULTIPLEXING_LIMIT;
    connectionWindowSize = HttpClientOptions.DEFAULT_HTTP2_CONNECTION_WINDOW_SIZE;
    keepAliveTimeout = Duration.ofSeconds(HttpClientOptions.DEFAULT_HTTP2_KEEP_ALIVE_TIMEOUT);
    upgradeMaxContentLength = HttpClientOptions.DEFAULT_HTTP2_UPGRADE_MAX_CONTENT_LENGTH;
    multiplexImplementation = HttpClientOptions.DEFAULT_HTTP_2_MULTIPLEX_IMPLEMENTATION;
    initialSettings = new Http2Settings();
    clearTextUpgrade = HttpClientOptions.DEFAULT_HTTP2_CLEAR_TEXT_UPGRADE;
    clearTextUpgradeWithPreflightRequest = HttpClientOptions.DEFAULT_HTTP2_CLEAR_TEXT_UPGRADE_WITH_PREFLIGHT_REQUEST;
  }

  public Http2ClientConfig(Http2ClientConfig other) {
    this.multiplexingLimit = other.multiplexingLimit;
    this.connectionWindowSize = other.connectionWindowSize;
    this.keepAliveTimeout = other.getKeepAliveTimeout();
    this.upgradeMaxContentLength = other.getUpgradeMaxContentLength();
    this.multiplexImplementation = other.getMultiplexImplementation();
    this.initialSettings = other.initialSettings != null ? new Http2Settings(other.initialSettings) : null;
    this.clearTextUpgrade = other.clearTextUpgrade;
    this.clearTextUpgradeWithPreflightRequest = other.clearTextUpgradeWithPreflightRequest;
  }

  /**
   * @return the maximum number of concurrent streams for an HTTP/2 connection, {@code -1} means
   * the value sent by the server
   */
  public int getMultiplexingLimit() {
    return multiplexingLimit;
  }

  /**
   * Set a client limit of the number concurrent streams for each HTTP/2 connection, this limits the number
   * of streams the client can create for a connection. The effective number of streams for a
   * connection is the min of this value and the server's initial settings.
   * <p/>
   * Setting the value to {@code -1} means to use the value sent by the server's initial settings.
   * {@code -1} is the default value.
   *
   * @param limit the maximum concurrent for an HTTP/2 connection
   * @return a reference to this, so the API can be used fluently
   */
  public Http2ClientConfig setMultiplexingLimit(int limit) {
    if (limit == 0 || limit < -1) {
      throw new IllegalArgumentException("maxPoolSize must be > 0 or -1 (disabled)");
    }
    this.multiplexingLimit = limit;
    return this;
  }

  /**
   * @return the default HTTP/2 connection window size
   */
  public int getConnectionWindowSize() {
    return connectionWindowSize;
  }

  /**
   * Set the default HTTP/2 connection window size. It overrides the initial window
   * size set by {@link Http2Settings#getInitialWindowSize}, so the connection window size
   * is greater than for its streams, in order the data throughput.
   * <p/>
   * A value of {@code -1} reuses the initial window size setting.
   *
   * @param connectionWindowSize the window size applied to the connection
   * @return a reference to this, so the API can be used fluently
   */
  public Http2ClientConfig setConnectionWindowSize(int connectionWindowSize) {
    this.connectionWindowSize = connectionWindowSize;
    return this;
  }

  /**
   * @return the keep alive timeout value in seconds for HTTP/2 connections
   */
  public Duration getKeepAliveTimeout() {
    return keepAliveTimeout;
  }

  /**
   * Set the keep alive timeout for HTTP/2 connections.
   * <p/>
   * This value determines how long a connection remains unused in the pool before being evicted and closed.
   * <p/>
   * A timeout of {@code null} means there is no timeout.
   *
   * @param keepAliveTimeout the timeout, in seconds
   * @return a reference to this, so the API can be used fluently
   */
  public Http2ClientConfig setKeepAliveTimeout(Duration keepAliveTimeout) {
    if (keepAliveTimeout != null && (keepAliveTimeout.isNegative() || keepAliveTimeout.isZero())) {
      throw new IllegalArgumentException("HTTP/2 keepAliveTimeout must be >= 0");
    }
    this.keepAliveTimeout = keepAliveTimeout;
    return this;
  }

  /**
   * @return the HTTP/2 upgrade maximum length of the aggregated content in bytes
   */
  public int getUpgradeMaxContentLength() {
    return upgradeMaxContentLength;
  }

  /**
   * Set the HTTP/2 upgrade maximum length of the aggregated content in bytes.
   * This is only taken into account when {@link Http2ClientConfig#isClearTextUpgradeWithPreflightRequest} is set to {@code false} (which is the default).
   * When {@link Http2ClientConfig#isClearTextUpgradeWithPreflightRequest} is {@code true}, then the client makes a preflight OPTIONS request
   * and the upgrade will not send a body, voiding the requirements.
   *
   * @param upgradeMaxContentLength the length, in bytes
   * @return a reference to this, so the API can be used fluently
   */
  public Http2ClientConfig setUpgradeMaxContentLength(int upgradeMaxContentLength) {
    this.upgradeMaxContentLength = upgradeMaxContentLength;
    return this;
  }

  /**
   * @return whether to use the HTTP/2 implementation based on multiplexed channel
   */
  public boolean getMultiplexImplementation() {
    return multiplexImplementation;
  }

  /**
   * Set which HTTP/2 implementation to use
   *
   * @param multiplexImplementation whether to use the HTTP/2 multiplex implementation
   * @return a reference to this, so the API can be used fluently
   */
  public Http2ClientConfig setMultiplexImplementation(boolean multiplexImplementation) {
    this.multiplexImplementation = multiplexImplementation;
    return this;
  }

  /**
   * @return the initial HTTP/2 connection settings
   */
  public Http2Settings getInitialSettings() {
    return initialSettings;
  }

  /**
   * Set the HTTP/2 connection settings immediately sent by to the server when the client connects.
   *
   * @param settings the settings value
   * @return a reference to this, so the API can be used fluently
   */
  public Http2ClientConfig setInitialSettings(Http2Settings settings) {
    this.initialSettings = settings;
    return this;
  }

  /**
   * @return {@code true} when an <i>h2c</i> connection is established using an HTTP/1.1 upgrade request, {@code false} when directly
   */
  public boolean isClearTextUpgrade() {
    return clearTextUpgrade;
  }

  /**
   * Set to {@code true} when an <i>h2c</i> connection is established using an HTTP/1.1 upgrade request, and {@code false}
   * when an <i>h2c</i> connection is established directly (with prior knowledge).
   *
   * @param value the upgrade value
   * @return a reference to this, so the API can be used fluently
   */
  public Http2ClientConfig setClearTextUpgrade(boolean value) {
    this.clearTextUpgrade = value;
    return this;
  }

  /**
   * @return {@code true} when an <i>h2c</i> connection established using an HTTP/1.1 upgrade request should perform
   *         a preflight {@code OPTIONS} request to the origin server to establish the <i>h2c</i> connection
   */
  public boolean isClearTextUpgradeWithPreflightRequest() {
    return clearTextUpgradeWithPreflightRequest;
  }

  /**
   * Set to {@code true} when an <i>h2c</i> connection established using an HTTP/1.1 upgrade request should perform
   * a preflight {@code OPTIONS} request to the origin server to establish the <i>h2c</i> connection.
   *
   * @param value the upgrade value
   * @return a reference to this, so the API can be used fluently
   */
  public Http2ClientConfig setClearTextUpgradeWithPreflightRequest(boolean value) {
    this.clearTextUpgradeWithPreflightRequest = value;
    return this;
  }
}
