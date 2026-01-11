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

import io.vertx.core.http.HttpClientOptions;

import java.time.Duration;

/**
 * HTTP/3 client configuration.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http3ClientConfig {

  private Duration keepAliveTimeout;

  public Http3ClientConfig() {
    keepAliveTimeout = Duration.ofSeconds(HttpClientOptions.DEFAULT_HTTP2_KEEP_ALIVE_TIMEOUT);
  }

  public Http3ClientConfig(Http3ClientConfig config) {
    this.keepAliveTimeout = config.keepAliveTimeout;
  }

  /**
   * @return the keep alive timeout value in seconds for HTTP/2 connections
   */
  public Duration getKeepAliveTimeout() {
    return keepAliveTimeout;
  }

  /**
   * Set the keep alive timeout for HTTP/3 connections.
   * <p/>
   * This value determines how long a connection remains unused in the pool before being evicted and closed.
   * <p/>
   * A timeout of {@code null} means there is no timeout.
   *
   * @param keepAliveTimeout the timeout, in seconds
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ClientConfig setKeepAliveTimeout(Duration keepAliveTimeout) {
    if (keepAliveTimeout != null && (keepAliveTimeout.isNegative() || keepAliveTimeout.isZero())) {
      throw new IllegalArgumentException("HTTP/3 keepAliveTimeout must be >= 0");
    }
    this.keepAliveTimeout = keepAliveTimeout;
    return this;
  }
}
