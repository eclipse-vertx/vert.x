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
import io.vertx.core.http.impl.config.Http3ClientConfig;
import io.vertx.core.net.*;

import java.time.Duration;

/**
 * Options describing how an {@link HttpClient} will make connections with HTTP/3.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class Http3ClientOptions extends QuicClientOptions {

  /**
   * The default value for host name = "localhost"
   */
  public static final String DEFAULT_DEFAULT_HOST = "localhost";

  /**
   * The default value for port = 443
   */
  public static final int DEFAULT_DEFAULT_PORT = 443;

  /**
   * The default value for verify host = true;
   */
  public static final boolean DEFAULT_VERIFY_HOST = true;

  /**
   * The default value for max directs = 16;
   */
  public static final int DEFAULT_MAX_REDIRECTS = 16;

  private int defaultPort;
  private String defaultHost;
  private boolean verifyHost;
  private int maxRedirects;
  private String metricsName;
  private Duration keepAliveTimeout;
  private Http3Settings initialSettings;

  public Http3ClientOptions() {
    this.defaultPort = DEFAULT_DEFAULT_PORT;
    this.defaultHost = DEFAULT_DEFAULT_HOST;
    this.verifyHost = DEFAULT_VERIFY_HOST;
    this.maxRedirects = DEFAULT_MAX_REDIRECTS;
    this.keepAliveTimeout = Duration.ofSeconds(HttpClientOptions.DEFAULT_HTTP2_KEEP_ALIVE_TIMEOUT);
    this.initialSettings = null;
  }

  public Http3ClientOptions(Http3ClientOptions other) {
    super(other);

    this.defaultPort = other.defaultPort;
    this.defaultHost = other.defaultHost;
    this.verifyHost = other.verifyHost;
    this.maxRedirects = other.maxRedirects;
    this.metricsName = other.metricsName;
    this.keepAliveTimeout = other.keepAliveTimeout;
    this.initialSettings = other.initialSettings != null ? other.initialSettings.copy() : null;
  }

  @Override
  public Http3ClientOptions setTransportOptions(QuicOptions transportOptions) {
    return (Http3ClientOptions)super.setTransportOptions(transportOptions);
  }

  @Override
  public Http3ClientOptions setQLogConfig(QLogConfig qLogConfig) {
    return (Http3ClientOptions)super.setQLogConfig(qLogConfig);
  }

  @Override
  public Http3ClientOptions setKeyLogFile(String keyLogFile) {
    return (Http3ClientOptions)super.setKeyLogFile(keyLogFile);
  }

  @Override
  public Http3ClientOptions setConnectTimeout(Duration connectTimeout) {
    return (Http3ClientOptions)super.setConnectTimeout(connectTimeout);
  }

  @Override
  public Http3ClientOptions setStreamIdleTimeout(Duration idleTimeout) {
    return (Http3ClientOptions)super.setStreamIdleTimeout(idleTimeout);
  }

  @Override
  public Http3ClientOptions setStreamReadIdleTimeout(Duration idleTimeout) {
    return (Http3ClientOptions)super.setStreamReadIdleTimeout(idleTimeout);
  }

  @Override
  public Http3ClientOptions setStreamWriteIdleTimeout(Duration idleTimeout) {
    return (Http3ClientOptions)super.setStreamWriteIdleTimeout(idleTimeout);
  }

  @Override
  public Http3ClientOptions setSslOptions(ClientSSLOptions sslOptions) {
    return (Http3ClientOptions)super.setSslOptions(sslOptions);
  }

  /**
   * Get the default host name to be used by this client in requests if none is provided when making the request.
   *
   * @return  the default host name
   */
  public String getDefaultHost() {
    return defaultHost;
  }

  /**
   * Set the default host name to be used by this client in requests if none is provided when making the request.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ClientOptions setDefaultHost(String defaultHost) {
    this.defaultHost = defaultHost;
    return this;
  }

  /**
   * Get the default port to be used by this client in requests if none is provided when making the request.
   *
   * @return  the default port
   */
  public int getDefaultPort() {
    return defaultPort;
  }

  /**
   * Set the default port to be used by this client in requests if none is provided when making the request.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ClientOptions setDefaultPort(int defaultPort) {
    this.defaultPort = defaultPort;
    return this;
  }

  /**
   * Is hostname verification (for SSL/TLS) enabled?
   *
   * @return {@code true} if enabled
   */
  public boolean isVerifyHost() {
    return verifyHost;
  }

  /**
   * Set whether hostname verification is enabled
   *
   * @param verifyHost {@code true} if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ClientOptions setVerifyHost(boolean verifyHost) {
    this.verifyHost = verifyHost;
    return this;
  }

  /**
   * @return the maximum number of redirection a request can follow
   */
  public int getMaxRedirects() {
    return maxRedirects;
  }

  /**
   * Set to {@code maxRedirects} the maximum number of redirection a request can follow.
   *
   * @param maxRedirects the maximum number of redirection
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ClientOptions setMaxRedirects(int maxRedirects) {
    this.maxRedirects = maxRedirects;
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
  public Http3ClientOptions setMetricsName(String metricsName) {
    this.metricsName = metricsName;
    return this;
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
  public Http3ClientOptions setKeepAliveTimeout(Duration keepAliveTimeout) {
    if (keepAliveTimeout != null && (keepAliveTimeout.isNegative() || keepAliveTimeout.isZero())) {
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
  public Http3ClientOptions setInitialSettings(Http3Settings settings) {
    this.initialSettings = settings;
    return this;
  }
}
