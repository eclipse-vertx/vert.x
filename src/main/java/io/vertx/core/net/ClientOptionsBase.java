/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

import java.util.Objects;

/**
 * Base class for Client options
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true)
public abstract class ClientOptionsBase extends TCPSSLOptions {

  /**
   * The default value of connect timeout = 60000 ms
   */
  public static final int DEFAULT_CONNECT_TIMEOUT = 60000;

  /**
   * The default value of whether all servers (SSL/TLS) should be trusted = false
   */
  public static final boolean DEFAULT_TRUST_ALL = false;

  /**
   * The default value of the client metrics = "":
   */
  public static final String DEFAULT_METRICS_NAME = "";

  private int connectTimeout;
  private boolean trustAll;
  private String metricsName;

  private ProxyOptions proxyOptions;

  /**
   * Default constructor
   */
  public ClientOptionsBase() {
    super();
    init();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public ClientOptionsBase(ClientOptionsBase other) {
    super(other);
    this.connectTimeout = other.getConnectTimeout();
    this.trustAll = other.isTrustAll();
    this.metricsName = other.metricsName;
    this.proxyOptions = other.proxyOptions != null ? new ProxyOptions(other.proxyOptions) : null;
  }

  /**
   * Create options from some JSON
   *
   * @param json  the JSON
   */
  public ClientOptionsBase(JsonObject json) {
    super(json);
    init();
    ClientOptionsBaseConverter.fromJson(json, this);
  }

  private void init() {
    this.connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    this.trustAll = DEFAULT_TRUST_ALL;
    this.metricsName = DEFAULT_METRICS_NAME;
    this.proxyOptions = null;
  }

  /**
   *
   * @return true if all server certificates should be trusted
   */
  public boolean isTrustAll() {
    return trustAll;
  }

  /**
   * Set whether all server certificates should be trusted
   *
   * @param trustAll true if all should be trusted
   * @return a reference to this, so the API can be used fluently
   */
  public ClientOptionsBase setTrustAll(boolean trustAll) {
    this.trustAll = trustAll;
    return this;
  }

  /**
   * @return the value of connect timeout
   */
  public int getConnectTimeout() {
    return connectTimeout;
  }

  /**
   * Set the connect timeout
   *
   * @param connectTimeout  connect timeout, in ms
   * @return a reference to this, so the API can be used fluently
   */
  public ClientOptionsBase setConnectTimeout(int connectTimeout) {
    if (connectTimeout < 0) {
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
  public ClientOptionsBase setMetricsName(String metricsName) {
    this.metricsName = metricsName;
    return this;
  }

  /**
   * Set proxy options for connections via CONNECT proxy (e.g. Squid) or a SOCKS proxy.
   *
   * @param proxyOptions proxy options object
   * @return a reference to this, so the API can be used fluently
   */
  public ClientOptionsBase setProxyOptions(ProxyOptions proxyOptions) {
    this.proxyOptions = proxyOptions;
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

  @Override
  public ClientOptionsBase setLogActivity(boolean logEnabled) {
    return (ClientOptionsBase) super.setLogActivity(logEnabled);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ClientOptionsBase)) return false;
    if (!super.equals(o)) return false;

    ClientOptionsBase that = (ClientOptionsBase) o;

    if (connectTimeout != that.connectTimeout) return false;
    if (trustAll != that.trustAll) return false;
    if (!Objects.equals(metricsName, that.metricsName)) return false;
    if (!Objects.equals(proxyOptions, that.proxyOptions)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + connectTimeout;
    result = 31 * result + (trustAll ? 1 : 0);
    result = 31 * result + (metricsName != null ? metricsName.hashCode() : 0);
    result = 31 * result + (proxyOptions != null ? proxyOptions.hashCode() : 0);
    return result;
  }
}
