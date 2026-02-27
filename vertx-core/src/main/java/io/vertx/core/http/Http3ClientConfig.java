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
package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;

import java.time.Duration;

/**
 * HTTP/3 client configuration.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class Http3ClientConfig {

  private int multiplexingLimit;
  private Duration keepAliveTimeout;
  private Http3Settings initialSettings;

  public Http3ClientConfig() {
    multiplexingLimit = HttpClientOptions.DEFAULT_HTTP2_MULTIPLEXING_LIMIT;
    keepAliveTimeout = Duration.ofSeconds(HttpClientOptions.DEFAULT_HTTP2_KEEP_ALIVE_TIMEOUT);
    initialSettings = null;
  }

  public Http3ClientConfig(Http3ClientConfig config) {
    this.multiplexingLimit = config.multiplexingLimit;
    this.keepAliveTimeout = config.keepAliveTimeout;
    this.initialSettings = config.initialSettings != null ? config.initialSettings.copy() : null;
  }

  /**
   * @return the maximum number of concurrent streams for an HTTP/3 connection, {@code -1} means
   * the value sent by the server
   */
  public int getMultiplexingLimit() {
    return multiplexingLimit;
  }

  /**
   * Set a client limit of the number concurrent streams for each HTTP/3 connection, this limits the number
   * of streams the client can create for a connection. The effective number of streams for a
   * connection is the min of this value and the server's initial settings.
   * <p/>
   * Setting the value to {@code -1} means to use the value sent by the server's initial settings.
   * {@code -1} is the default value.
   *
   * @param limit the maximum concurrent for an HTTP/3 connection
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ClientConfig setMultiplexingLimit(int limit) {
    if (limit == 0 || limit < -1) {
      throw new IllegalArgumentException("multiplexingLimit must be > 0 or -1 (disabled)");
    }
    this.multiplexingLimit = limit;
    return this;
  }

  /**
   * @return the keep alive timeout value in seconds for HTTP/3 connections
   */
  public Duration getKeepAliveTimeout() {
    return keepAliveTimeout;
  }

  /**
   * <p>Set the keep alive timeout for HTTP/3 connections. This value determines how long a connection remains
   * unused in the pool before being evicted and closed. A timeout of zero or {@code null} means there is no timeout.</p>
   *
   * @param keepAliveTimeout the timeout, in seconds
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ClientConfig setKeepAliveTimeout(Duration keepAliveTimeout) {
    if (keepAliveTimeout != null && (keepAliveTimeout.isNegative())) {
      throw new IllegalArgumentException("HTTP/3 keepAliveTimeout must be >= 0");
    }
    this.keepAliveTimeout = keepAliveTimeout;
    return this;
  }

  /**
   * @return the initial HTTP/3 connection settings sent by the client
   */
  public Http3Settings getInitialSettings() {
    return initialSettings;
  }

  /**
   * Set the HTTP/3 connection settings sent by the client.
   *
   * @param settings the settings value
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ClientConfig setInitialSettings(Http3Settings settings) {
    this.initialSettings = settings;
    return this;
  }
}
