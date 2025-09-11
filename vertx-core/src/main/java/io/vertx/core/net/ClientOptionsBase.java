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
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.JsonObject;
import io.netty.handler.logging.ByteBufFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.vertx.core.http.HttpClientOptions.DEFAULT_PROTOCOL_VERSION;

/**
 * Base class for Client options
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public abstract class ClientOptionsBase extends TCPSSLOptions {

  /**
   * The default value of connect timeout = 60000 (ms)
   */
  public static final int DEFAULT_CONNECT_TIMEOUT = 60000;

  /**
   * The default value of the client metrics = "":
   */
  public static final String DEFAULT_METRICS_NAME = "";

  private int connectTimeout;
  private String metricsName;
  private ProxyOptions proxyOptions;
  private String localAddress;
  private List<String> nonProxyHosts;
  private QuicOptions quicOptions;

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
    this.metricsName = other.metricsName;
    this.proxyOptions = other.proxyOptions != null ? new ProxyOptions(other.proxyOptions) : null;
    this.localAddress = other.localAddress;
    this.nonProxyHosts = other.nonProxyHosts != null ? new ArrayList<>(other.nonProxyHosts) : null;
    this.quicOptions = other.getQuicOptions() != null ? other.getQuicOptions().copy() : null;
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
    this.metricsName = DEFAULT_METRICS_NAME;
    this.proxyOptions = null;
    this.localAddress = null;
    this.quicOptions = new QuicOptions();
  }

  @GenIgnore
  @Override
  public ClientSSLOptions getSslOptions() {
    return (ClientSSLOptions) super.getSslOptions();
  }

  @Override
  protected ClientSSLOptions getOrCreateSSLOptions() {
    return (ClientSSLOptions) super.getOrCreateSSLOptions();
  }

  /**
   *
   * @return true if all server certificates should be trusted
   */
  public boolean isTrustAll() {
    ClientSSLOptions o = getSslOptions();
    return o != null ? o.isTrustAll() : ClientSSLOptions.DEFAULT_TRUST_ALL;
  }

  /**
   * Set whether all server certificates should be trusted
   *
   * @param trustAll true if all should be trusted
   * @return a reference to this, so the API can be used fluently
   */
  public ClientOptionsBase setTrustAll(boolean trustAll) {
    getOrCreateSSLOptions().setTrustAll(trustAll);
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
   * @return the list of non proxies hosts
   */
  public List<String> getNonProxyHosts() {
    return nonProxyHosts;
  }

  /**
   * Set a list of remote hosts that are not proxied when the client is configured to use a proxy. This
   * list serves the same purpose than the JVM {@code nonProxyHosts} configuration.
   *
   * <p> Entries can use the <i>*</i> wildcard character for pattern matching, e.g <i>*.example.com</i> matches
   * <i>www.example.com</i>.
   *
   * @param nonProxyHosts the list of non proxies hosts
   * @return a reference to this, so the API can be used fluently
   */
  public ClientOptionsBase setNonProxyHosts(List<String> nonProxyHosts) {
    this.nonProxyHosts = nonProxyHosts;
    return this;
  }

  /**
   * Add a {@code host} to the {@link #getNonProxyHosts()} list.
   *
   * @param host the added host
   * @return a reference to this, so the API can be used fluently
   */
  public ClientOptionsBase addNonProxyHost(String host) {
    if (nonProxyHosts == null) {
      nonProxyHosts = new ArrayList<>();
    }
    nonProxyHosts.add(host);
    return this;
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
  public ClientOptionsBase setActivityLogDataFormat(ByteBufFormat activityLogDataFormat) {
    return (ClientOptionsBase) super.setActivityLogDataFormat(activityLogDataFormat);
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
  public ClientOptionsBase setIdleTimeout(int idleTimeout) {
    return (ClientOptionsBase) super.setIdleTimeout(idleTimeout);
  }

  @Override
  public ClientOptionsBase setReadIdleTimeout(int idleTimeout) {
    return (ClientOptionsBase) super.setReadIdleTimeout(idleTimeout);
  }

  @Override
  public ClientOptionsBase setWriteIdleTimeout(int idleTimeout) {
    return (ClientOptionsBase) super.setWriteIdleTimeout(idleTimeout);
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
  public ClientOptionsBase setTrustOptions(TrustOptions options) {
    return (ClientOptionsBase) super.setTrustOptions(options);
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
  public ClientOptionsBase removeEnabledCipherSuite(String suite) {
    return (ClientOptionsBase) super.removeEnabledCipherSuite(suite);
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
  public ClientOptionsBase setTcpUserTimeout(int tcpUserTimeout) {
    return (ClientOptionsBase) super.setTcpUserTimeout(tcpUserTimeout);
  }

  public ClientOptionsBase setSslHandshakeTimeout(long sslHandshakeTimeout) {
    return (ClientOptionsBase) super.setSslHandshakeTimeout(sslHandshakeTimeout);
  }

  public ClientOptionsBase setSslHandshakeTimeoutUnit(TimeUnit sslHandshakeTimeoutUnit) {
    return (ClientOptionsBase) super.setSslHandshakeTimeoutUnit(sslHandshakeTimeoutUnit);
  }

  /**
   * @return  the value of quicOptions
   */
  public QuicOptions getQuicOptions() {
    return quicOptions;
  }

  /**
   * Set the value of quicOptions
   *
   * @param quicOptions
   * @return a reference to this, so the API can be used fluently
   */
  public ClientOptionsBase setQuicOptions(QuicOptions quicOptions) {
    this.quicOptions = quicOptions;
    return this;
  }

}
