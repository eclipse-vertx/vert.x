/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Options for configuring a {@link io.vertx.core.net.NetClient}.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true, publicConverter = false)
public class NetClientOptions extends ClientOptionsBase {

  /**
   * The default value for reconnect attempts = 0
   */
  public static final int DEFAULT_RECONNECT_ATTEMPTS = 0;

  /**
   * The default value for reconnect interval = 1000 ms
   */
  public static final long DEFAULT_RECONNECT_INTERVAL = 1000;

  /**
   * Default value to determine hostname verification algorithm hostname verification (for SSL/TLS) = ""
   */
  public static final String DEFAULT_HOSTNAME_VERIFICATION_ALGORITHM = "";


  private int reconnectAttempts;
  private long reconnectInterval;
  private String hostnameVerificationAlgorithm;
  private List<String> applicationLayerProtocols;

    /**
   * The default constructor
   */
  public NetClientOptions() {
    super();
    init();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public NetClientOptions(NetClientOptions other) {
    super(other);
    this.reconnectAttempts = other.getReconnectAttempts();
    this.reconnectInterval = other.getReconnectInterval();
    this.hostnameVerificationAlgorithm = other.getHostnameVerificationAlgorithm();
    this.applicationLayerProtocols = other.applicationLayerProtocols != null ? new ArrayList<>(other.applicationLayerProtocols) : null;
  }

  /**
   * Create options from JSON
   *
   * @param json  the JSON
   */
  public NetClientOptions(JsonObject json) {
    super(json);
    init();
    NetClientOptionsConverter.fromJson(json, this);
  }

  private void init() {
    this.reconnectAttempts = DEFAULT_RECONNECT_ATTEMPTS;
    this.reconnectInterval = DEFAULT_RECONNECT_INTERVAL;
    this.hostnameVerificationAlgorithm = DEFAULT_HOSTNAME_VERIFICATION_ALGORITHM;
  }

  @Override
  public NetClientOptions setSendBufferSize(int sendBufferSize) {
    super.setSendBufferSize(sendBufferSize);
    return this;
  }

  @Override
  public NetClientOptions setReceiveBufferSize(int receiveBufferSize) {
    super.setReceiveBufferSize(receiveBufferSize);
    return this;
  }

  @Override
  public NetClientOptions setReuseAddress(boolean reuseAddress) {
    super.setReuseAddress(reuseAddress);
    return this;
  }

  @Override
  public NetClientOptions setReusePort(boolean reusePort) {
    super.setReusePort(reusePort);
    return this;
  }

  @Override
  public NetClientOptions setTrafficClass(int trafficClass) {
    super.setTrafficClass(trafficClass);
    return this;
  }

  @Override
  public NetClientOptions setTcpNoDelay(boolean tcpNoDelay) {
    super.setTcpNoDelay(tcpNoDelay);
    return this;
  }

  @Override
  public NetClientOptions setTcpKeepAlive(boolean tcpKeepAlive) {
    super.setTcpKeepAlive(tcpKeepAlive);
    return this;
  }

  @Override
  public NetClientOptions setSoLinger(int soLinger) {
    super.setSoLinger(soLinger);
    return this;
  }

  @Override
  public NetClientOptions setIdleTimeout(int idleTimeout) {
    super.setIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public NetClientOptions setIdleTimeoutUnit(TimeUnit idleTimeoutUnit) {
    super.setIdleTimeoutUnit(idleTimeoutUnit);
    return this;
  }

  @Override
  public NetClientOptions setSsl(boolean ssl) {
    super.setSsl(ssl);
    return this;
  }

  @Override
  public NetClientOptions setKeyCertOptions(KeyCertOptions options) {
    super.setKeyCertOptions(options);
    return this;
  }

  @Override
  public NetClientOptions setKeyStoreOptions(JksOptions options) {
    super.setKeyStoreOptions(options);
    return this;
  }

  @Override
  public NetClientOptions setPfxKeyCertOptions(PfxOptions options) {
    return (NetClientOptions) super.setPfxKeyCertOptions(options);
  }

  @Override
  public NetClientOptions setPemKeyCertOptions(PemKeyCertOptions options) {
    return (NetClientOptions) super.setPemKeyCertOptions(options);
  }

  @Override
  public NetClientOptions setTrustOptions(TrustOptions options) {
    super.setTrustOptions(options);
    return this;
  }

  @Override
  public NetClientOptions setTrustStoreOptions(JksOptions options) {
    super.setTrustStoreOptions(options);
    return this;
  }

  @Override
  public NetClientOptions setPemTrustOptions(PemTrustOptions options) {
    return (NetClientOptions) super.setPemTrustOptions(options);
  }

  @Override
  public NetClientOptions setPfxTrustOptions(PfxOptions options) {
    return (NetClientOptions) super.setPfxTrustOptions(options);
  }

  @Override
  public NetClientOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
    return this;
  }

  @Override
  public NetClientOptions addEnabledSecureTransportProtocol(final String protocol) {
    super.addEnabledSecureTransportProtocol(protocol);
    return this;
  }

  @Override
  public NetClientOptions removeEnabledSecureTransportProtocol(String protocol) {
    return (NetClientOptions) super.removeEnabledSecureTransportProtocol(protocol);
  }

  @Override
  public NetClientOptions setUseAlpn(boolean useAlpn) {
    return (NetClientOptions) super.setUseAlpn(useAlpn);
  }

  @Override
  public NetClientOptions setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
    return (NetClientOptions) super.setSslEngineOptions(sslEngineOptions);
  }

  @Override
  public NetClientOptions setJdkSslEngineOptions(JdkSSLEngineOptions sslEngineOptions) {
    return (NetClientOptions) super.setJdkSslEngineOptions(sslEngineOptions);
  }

  @Override
  public NetClientOptions setTcpFastOpen(boolean tcpFastOpen) {
    return (NetClientOptions) super.setTcpFastOpen(tcpFastOpen);
  }

  @Override
  public NetClientOptions setTcpCork(boolean tcpCork) {
    return (NetClientOptions) super.setTcpCork(tcpCork);
  }

  @Override
  public NetClientOptions setTcpQuickAck(boolean tcpQuickAck) {
    return (NetClientOptions) super.setTcpQuickAck(tcpQuickAck);
  }

  @Override
  public ClientOptionsBase setOpenSslEngineOptions(OpenSSLEngineOptions sslEngineOptions) {
    return super.setOpenSslEngineOptions(sslEngineOptions);
  }

  @Override
  public NetClientOptions addCrlPath(String crlPath) throws NullPointerException {
    return (NetClientOptions) super.addCrlPath(crlPath);
  }

  @Override
  public NetClientOptions addCrlValue(Buffer crlValue) throws NullPointerException {
    return (NetClientOptions) super.addCrlValue(crlValue);
  }

  @Override
  public NetClientOptions setTrustAll(boolean trustAll) {
    super.setTrustAll(trustAll);
    return this;
  }

  @Override
  public NetClientOptions setConnectTimeout(int connectTimeout) {
    super.setConnectTimeout(connectTimeout);
    return this;
  }

  @Override
  public NetClientOptions setMetricsName(String metricsName) {
    return (NetClientOptions) super.setMetricsName(metricsName);
  }

  /**
   * Set the value of reconnect attempts
   *
   * @param attempts  the maximum number of reconnect attempts
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientOptions setReconnectAttempts(int attempts) {
    if (attempts < -1) {
      throw new IllegalArgumentException("reconnect attempts must be >= -1");
    }
    this.reconnectAttempts = attempts;
    return this;
  }

  /**
   * @return  the value of reconnect attempts
   */
  public int getReconnectAttempts() {
    return reconnectAttempts;
  }

  /**
   * Set the reconnect interval
   *
   * @param interval  the reconnect interval in ms
   * @return a reference to this, so the API can be used fluently
   */
  public NetClientOptions setReconnectInterval(long interval) {
    if (interval < 1) {
      throw new IllegalArgumentException("reconnect interval must be >= 1");
    }
    this.reconnectInterval = interval;
    return this;
  }

  /**
   * @return  the value of the hostname verification algorithm
   */

  public String getHostnameVerificationAlgorithm() {
    return hostnameVerificationAlgorithm;
  }

  /**
   * Set the hostname verification algorithm interval
   * To disable hostname verification, set hostnameVerificationAlgorithm to an empty String
   *
   * @param hostnameVerificationAlgorithm should be HTTPS, LDAPS or an empty String
   * @return a reference to this, so the API can be used fluently
   */

  public NetClientOptions setHostnameVerificationAlgorithm(String hostnameVerificationAlgorithm) {
    Objects.requireNonNull(hostnameVerificationAlgorithm, "hostnameVerificationAlgorithm can not be null!");
    this.hostnameVerificationAlgorithm = hostnameVerificationAlgorithm;
    return this;
  }

  /**
   * @return the list of application-layer protocols send during the Application-Layer Protocol Negotiation.
   */
  public List<String> getApplicationLayerProtocols() {
    return applicationLayerProtocols;
  }

  /**
   * Set the list of application-layer protocols to provide to the server during the Application-Layer Protocol Negotiation.
   *
   * @param protocols the protocols
   * @return a reference to this, so the API can be used fluently
   */
  public ClientOptionsBase setApplicationLayerProtocols(List<String> protocols) {
    this.applicationLayerProtocols = protocols;
    return this;
  }

  /**
   * @return  the value of reconnect interval
   */
  public long getReconnectInterval() {
    return reconnectInterval;
  }

  @Override
  public NetClientOptions setLogActivity(boolean logEnabled) {
    return (NetClientOptions) super.setLogActivity(logEnabled);
  }

  public NetClientOptions setProxyOptions(ProxyOptions proxyOptions) {
    return (NetClientOptions) super.setProxyOptions(proxyOptions);
  }

  @Override
  public NetClientOptions setLocalAddress(String localAddress) {
    return (NetClientOptions) super.setLocalAddress(localAddress);
  }

  @Override
  public NetClientOptions setEnabledSecureTransportProtocols(Set<String> enabledSecureTransportProtocols) {
    return (NetClientOptions) super.setEnabledSecureTransportProtocols(enabledSecureTransportProtocols);
  }

  public NetClientOptions setSslHandshakeTimeout(long sslHandshakeTimeout) {
    return (NetClientOptions) super.setSslHandshakeTimeout(sslHandshakeTimeout);
  }

  public NetClientOptions setSslHandshakeTimeoutUnit(TimeUnit sslHandshakeTimeoutUnit) {
    return (NetClientOptions) super.setSslHandshakeTimeoutUnit(sslHandshakeTimeoutUnit);
  }
}
