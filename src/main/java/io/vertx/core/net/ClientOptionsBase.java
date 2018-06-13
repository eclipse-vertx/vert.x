/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Base class for Client options
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true, publicConverter = false)
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
  private String localAddress;

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
    this.localAddress = other.localAddress;
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

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = super.toJson();
    ClientOptionsBaseConverter.toJson(this, json);
    return json;
  }

  private void init() {
    this.connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    this.trustAll = DEFAULT_TRUST_ALL;
    this.metricsName = DEFAULT_METRICS_NAME;
    this.proxyOptions = null;
    this.localAddress = null;
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

  /**
   * @return the local interface to bind for network connections.
   */
  public String getLocalAddress() {
    return localAddress;
  }

  /**
   * Set the local interface to bind for network connections. When the local address is null,
   * it will pick any local address, the default local address is null.
   *
   * @param localAddress the local address
   * @return a reference to this, so the API can be used fluently
   */
  public ClientOptionsBase setLocalAddress(String localAddress) {
    this.localAddress = localAddress;
    return this;
  }

  @Override
  public ClientOptionsBase setLogActivity(boolean logEnabled) {
    return (ClientOptionsBase) super.setLogActivity(logEnabled);
  }

  @Override
  public ClientOptionsBase setTcpNoDelay(boolean tcpNoDelay) {
    return (ClientOptionsBase) super.setTcpNoDelay(tcpNoDelay);
  }

  @Override
  public ClientOptionsBase setTcpKeepAlive(boolean tcpKeepAlive) {
    return (ClientOptionsBase) super.setTcpKeepAlive(tcpKeepAlive);
  }

  @Override
  public ClientOptionsBase setSoLinger(int soLinger) {
    return (ClientOptionsBase) super.setSoLinger(soLinger);
  }

  @Override
  public ClientOptionsBase setUsePooledBuffers(boolean usePooledBuffers) {
    return (ClientOptionsBase) super.setUsePooledBuffers(usePooledBuffers);
  }

  @Override
  public ClientOptionsBase setIdleTimeout(int idleTimeout) {
    return (ClientOptionsBase) super.setIdleTimeout(idleTimeout);
  }

  @Override
  public ClientOptionsBase setIdleTimeoutUnit(TimeUnit idleTimeoutUnit) {
    return (ClientOptionsBase) super.setIdleTimeoutUnit(idleTimeoutUnit);
  }

  @Override
  public ClientOptionsBase setSsl(boolean ssl) {
    return (ClientOptionsBase) super.setSsl(ssl);
  }

  @Override
  public ClientOptionsBase setKeyCertOptions(KeyCertOptions options) {
    return (ClientOptionsBase) super.setKeyCertOptions(options);
  }

  @Override
  public ClientOptionsBase setKeyStoreOptions(JksOptions options) {
    return (ClientOptionsBase) super.setKeyStoreOptions(options);
  }

  @Override
  public ClientOptionsBase setPfxKeyCertOptions(PfxOptions options) {
    return (ClientOptionsBase) super.setPfxKeyCertOptions(options);
  }

  @Override
  public ClientOptionsBase setPemKeyCertOptions(PemKeyCertOptions options) {
    return (ClientOptionsBase) super.setPemKeyCertOptions(options);
  }

  @Override
  public ClientOptionsBase setTrustOptions(TrustOptions options) {
    return (ClientOptionsBase) super.setTrustOptions(options);
  }

  @Override
  public ClientOptionsBase setTrustStoreOptions(JksOptions options) {
    return (ClientOptionsBase) super.setTrustStoreOptions(options);
  }

  @Override
  public ClientOptionsBase setPfxTrustOptions(PfxOptions options) {
    return (ClientOptionsBase) super.setPfxTrustOptions(options);
  }

  @Override
  public ClientOptionsBase setPemTrustOptions(PemTrustOptions options) {
    return (ClientOptionsBase) super.setPemTrustOptions(options);
  }

  @Override
  public ClientOptionsBase setUseAlpn(boolean useAlpn) {
    return (ClientOptionsBase) super.setUseAlpn(useAlpn);
  }

  @Override
  public ClientOptionsBase setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
    return (ClientOptionsBase) super.setSslEngineOptions(sslEngineOptions);
  }

  @Override
  public ClientOptionsBase setJdkSslEngineOptions(JdkSSLEngineOptions sslEngineOptions) {
    return (ClientOptionsBase) super.setJdkSslEngineOptions(sslEngineOptions);
  }

  @Override
  public ClientOptionsBase setOpenSslEngineOptions(OpenSSLEngineOptions sslEngineOptions) {
    return (ClientOptionsBase) super.setOpenSslEngineOptions(sslEngineOptions);
  }

  @Override
  public ClientOptionsBase setSendBufferSize(int sendBufferSize) {
    return (ClientOptionsBase) super.setSendBufferSize(sendBufferSize);
  }

  @Override
  public ClientOptionsBase setReceiveBufferSize(int receiveBufferSize) {
    return (ClientOptionsBase) super.setReceiveBufferSize(receiveBufferSize);
  }

  @Override
  public ClientOptionsBase setReuseAddress(boolean reuseAddress) {
    return (ClientOptionsBase) super.setReuseAddress(reuseAddress);
  }

  @Override
  public ClientOptionsBase setReusePort(boolean reusePort) {
    return (ClientOptionsBase) super.setReusePort(reusePort);
  }

  @Override
  public ClientOptionsBase setTrafficClass(int trafficClass) {
    return (ClientOptionsBase) super.setTrafficClass(trafficClass);
  }

  @Override
  public ClientOptionsBase addEnabledCipherSuite(String suite) {
    return (ClientOptionsBase) super.addEnabledCipherSuite(suite);
  }

  @Override
  public ClientOptionsBase addCrlPath(String crlPath) throws NullPointerException {
    return (ClientOptionsBase) super.addCrlPath(crlPath);
  }

  @Override
  public ClientOptionsBase addCrlValue(Buffer crlValue) throws NullPointerException {
    return (ClientOptionsBase) super.addCrlValue(crlValue);
  }

  @Override
  public ClientOptionsBase addEnabledSecureTransportProtocol(String protocol) {
    return (ClientOptionsBase) super.addEnabledSecureTransportProtocol(protocol);
  }

  @Override
  public ClientOptionsBase removeEnabledSecureTransportProtocol(String protocol) {
    return (ClientOptionsBase) super.removeEnabledSecureTransportProtocol(protocol);
  }

  @Override
  public ClientOptionsBase setTcpFastOpen(boolean tcpFastOpen) {
    return (ClientOptionsBase) super.setTcpFastOpen(tcpFastOpen);
  }

  @Override
  public ClientOptionsBase setTcpCork(boolean tcpCork) {
    return (ClientOptionsBase) super.setTcpCork(tcpCork);
  }

  @Override
  public ClientOptionsBase setTcpQuickAck(boolean tcpQuickAck) {
    return (ClientOptionsBase) super.setTcpQuickAck(tcpQuickAck);
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
    if (!Objects.equals(localAddress, that.localAddress)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + connectTimeout;
    result = 31 * result + (trustAll ? 1 : 0);
    result = 31 * result + (metricsName != null ? metricsName.hashCode() : 0);
    result = 31 * result + (proxyOptions != null ? proxyOptions.hashCode() : 0);
    result = 31 * result + (localAddress != null ? localAddress.hashCode() : 0);
    return result;
  }
}
