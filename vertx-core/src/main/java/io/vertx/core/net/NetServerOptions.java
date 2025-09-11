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

import io.netty.handler.logging.ByteBufFormat;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.json.JsonObject;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Options for configuring a {@link io.vertx.core.net.NetServer}.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class NetServerOptions extends TCPSSLOptions {

  // Server specific HTTP stuff

  /**
   * The default port to listen on = 0 (meaning a random ephemeral free port will be chosen)
   */
  public static final int DEFAULT_PORT = 0;

  /**
   * The default host to listen on = "0.0.0.0" (meaning listen on all available interfaces).
   */
  public static final String DEFAULT_HOST = "0.0.0.0";

  /**
   * The default accept backlog = 1024
   */
  public static final int DEFAULT_ACCEPT_BACKLOG = -1;

  /**
   * Default value of whether the server supports HA PROXY protocol = false
   */
  public static final boolean DEFAULT_USE_PROXY_PROTOCOL = false;

  /**
   * The default value of HA PROXY protocol timeout = 10
   */
  public static final long DEFAULT_PROXY_PROTOCOL_TIMEOUT = 10L;

  /**
   * Default HA PROXY protocol time unit = SECONDS
   */
  public static final TimeUnit DEFAULT_PROXY_PROTOCOL_TIMEOUT_TIME_UNIT = TimeUnit.SECONDS;

  /**
   * Whether a write-handler should be registered by default = false.
   */
  public static final boolean DEFAULT_REGISTER_WRITE_HANDLER = false;

  private int port;
  private String host;
  private int acceptBacklog;
  private boolean useProxyProtocol;
  private long proxyProtocolTimeout;
  private TimeUnit proxyProtocolTimeoutUnit;
  private boolean registerWriteHandler;
  private TrafficShapingOptions trafficShapingOptions;
  private QuicOptions quicOptions;

  /**
   * Default constructor
   */
  public NetServerOptions() {
    super();
    init();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public NetServerOptions(NetServerOptions other) {
    super(other);
    this.port = other.getPort();
    this.host = other.getHost();
    this.acceptBacklog = other.getAcceptBacklog();
    this.useProxyProtocol = other.isUseProxyProtocol();
    this.proxyProtocolTimeout = other.proxyProtocolTimeout;
    this.proxyProtocolTimeoutUnit = other.getProxyProtocolTimeoutUnit() != null ?
      other.getProxyProtocolTimeoutUnit() :
      DEFAULT_PROXY_PROTOCOL_TIMEOUT_TIME_UNIT;
    this.registerWriteHandler = other.registerWriteHandler;
    this.trafficShapingOptions = other.getTrafficShapingOptions();
    this.quicOptions = other.getQuicOptions() != null ? other.getQuicOptions().copy() : null;
  }

  /**
   * Create some options from JSON
   *
   * @param json  the JSON
   */
  public NetServerOptions(JsonObject json) {
    super(json);
    init();
    NetServerOptionsConverter.fromJson(json, this);
  }

  /**
   * Copy these options.
   *
   * @return a copy of this
   */
  public NetServerOptions copy() {
    return new NetServerOptions(this);
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = super.toJson();
    NetServerOptionsConverter.toJson(this, json);
    return json;
  }

  @GenIgnore
  @Override
  public ServerSSLOptions getSslOptions() {
    return (ServerSSLOptions) super.getSslOptions();
  }

  @Override
  protected ServerSSLOptions getOrCreateSSLOptions() {
    return (ServerSSLOptions) super.getOrCreateSSLOptions();
  }

  @Override
  public NetServerOptions setSendBufferSize(int sendBufferSize) {
    super.setSendBufferSize(sendBufferSize);
    return this;
  }

  @Override
  public NetServerOptions setReceiveBufferSize(int receiveBufferSize) {
    super.setReceiveBufferSize(receiveBufferSize);
    return this;
  }

  @Override
  public NetServerOptions setReuseAddress(boolean reuseAddress) {
    super.setReuseAddress(reuseAddress);
    return this;
  }

  @Override
  public NetServerOptions setReusePort(boolean reusePort) {
    super.setReusePort(reusePort);
    return this;
  }

  @Override
  public NetServerOptions setTrafficClass(int trafficClass) {
    super.setTrafficClass(trafficClass);
    return this;
  }

  @Override
  public NetServerOptions setTcpNoDelay(boolean tcpNoDelay) {
    super.setTcpNoDelay(tcpNoDelay);
    return this;
  }

  @Override
  public NetServerOptions setTcpKeepAlive(boolean tcpKeepAlive) {
    super.setTcpKeepAlive(tcpKeepAlive);
    return this;
  }

  @Override
  public NetServerOptions setSoLinger(int soLinger) {
    super.setSoLinger(soLinger);
    return this;
  }

  @Override
  public NetServerOptions setIdleTimeout(int idleTimeout) {
    super.setIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public NetServerOptions setReadIdleTimeout(int idleTimeout) {
    super.setReadIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public NetServerOptions setWriteIdleTimeout(int idleTimeout) {
    super.setWriteIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public NetServerOptions setIdleTimeoutUnit(TimeUnit idleTimeoutUnit) {
    super.setIdleTimeoutUnit(idleTimeoutUnit);
    return this;
  }

  @Override
  public NetServerOptions setSsl(boolean ssl) {
    super.setSsl(ssl);
    return this;
  }

  @Override
  public NetServerOptions setUseAlpn(boolean useAlpn) {
    super.setUseAlpn(useAlpn);
    return this;
  }

  @Override
  public NetServerOptions setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
    super.setSslEngineOptions(sslEngineOptions);
    return this;
  }

  @Override
  public NetServerOptions setKeyCertOptions(KeyCertOptions options) {
    super.setKeyCertOptions(options);
    return this;
  }

  @Override
  public NetServerOptions setTrustOptions(TrustOptions options) {
    super.setTrustOptions(options);
    return this;
  }

  @Override
  public NetServerOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
    return this;
  }

  @Override
  public NetServerOptions removeEnabledCipherSuite(String suite) {
    super.removeEnabledCipherSuite(suite);
    return this;
  }

  @Override
  public NetServerOptions addEnabledSecureTransportProtocol(final String protocol) {
    super.addEnabledSecureTransportProtocol(protocol);
    return this;
  }

  @Override
  public NetServerOptions removeEnabledSecureTransportProtocol(String protocol) {
    return (NetServerOptions) super.removeEnabledSecureTransportProtocol(protocol);
  }

  @Override
  public NetServerOptions setTcpFastOpen(boolean tcpFastOpen) {
    return (NetServerOptions) super.setTcpFastOpen(tcpFastOpen);
  }

  @Override
  public NetServerOptions setTcpCork(boolean tcpCork) {
    return (NetServerOptions) super.setTcpCork(tcpCork);
  }

  @Override
  public NetServerOptions setTcpQuickAck(boolean tcpQuickAck) {
    return (NetServerOptions) super.setTcpQuickAck(tcpQuickAck);
  }

  @Override
  public NetServerOptions addCrlPath(String crlPath) throws NullPointerException {
    return (NetServerOptions) super.addCrlPath(crlPath);
  }

  @Override
  public NetServerOptions addCrlValue(Buffer crlValue) throws NullPointerException {
    return (NetServerOptions) super.addCrlValue(crlValue);
  }

  @Override
  public NetServerOptions setEnabledSecureTransportProtocols(Set<String> enabledSecureTransportProtocols) {
    return (NetServerOptions) super.setEnabledSecureTransportProtocols(enabledSecureTransportProtocols);
  }

  @Override
  public NetServerOptions setSslHandshakeTimeout(long sslHandshakeTimeout) {
    return (NetServerOptions) super.setSslHandshakeTimeout(sslHandshakeTimeout);
  }

  @Override
  public NetServerOptions setSslHandshakeTimeoutUnit(TimeUnit sslHandshakeTimeoutUnit) {
    return (NetServerOptions) super.setSslHandshakeTimeoutUnit(sslHandshakeTimeoutUnit);
  }

  /**
   * @return the value of accept backlog
   */
  public int getAcceptBacklog() {
    return acceptBacklog;
  }

  /**
   * Set the accept back log
   *
   * @param acceptBacklog accept backlog
   * @return a reference to this, so the API can be used fluently
   */
  public NetServerOptions setAcceptBacklog(int acceptBacklog) {
    this.acceptBacklog = acceptBacklog;
    return this;
  }

  /**
   *
   * @return the port
   */
  public int getPort() {
    return port;
  }

  /**
   * Set the port
   *
   * @param port  the port
   * @return a reference to this, so the API can be used fluently
   */
  public NetServerOptions setPort(int port) {
    if (port > 65535) {
      throw new IllegalArgumentException("port must be <= 65535");
    }
    this.port = port;
    return this;
  }

  /**
   *
   * @return the host
   */
  public String getHost() {
    return host;
  }

  /**
   * Set the host
   * @param host  the host
   * @return a reference to this, so the API can be used fluently
   */
  public NetServerOptions setHost(String host) {
    this.host = host;
    return this;
  }

  public ClientAuth getClientAuth() {
    ServerSSLOptions o = getSslOptions();
    return o != null ? o.getClientAuth() : ServerSSLOptions.DEFAULT_CLIENT_AUTH;
  }

  /**
   * Set whether client auth is required
   *
   * @param clientAuth One of "NONE, REQUEST, REQUIRED". If it's set to "REQUIRED" then server will require the
   *                   SSL cert to be presented otherwise it won't accept the request. If it's set to "REQUEST" then
   *                   it won't mandate the certificate to be presented, basically make it optional.
   * @return a reference to this, so the API can be used fluently
   */
  public NetServerOptions setClientAuth(ClientAuth clientAuth) {
    getOrCreateSSLOptions().setClientAuth(clientAuth);
    return this;
  }

  @Override
  public NetServerOptions setLogActivity(boolean logEnabled) {
    return (NetServerOptions) super.setLogActivity(logEnabled);
  }

  @Override
  public NetServerOptions setActivityLogDataFormat(ByteBufFormat activityLogDataFormat) {
    return (NetServerOptions) super.setActivityLogDataFormat(activityLogDataFormat);
  }

  /**
   * @return whether the server supports Server Name Indication
   */
  public boolean isSni() {
    ServerSSLOptions o = getSslOptions();
    return o != null ? o.isSni() : ServerSSLOptions.DEFAULT_SNI;
  }

  /**
   * Set whether the server supports Server Name Indiciation
   *
   * @return a reference to this, so the API can be used fluently
   */
  public NetServerOptions setSni(boolean sni) {
    getOrCreateSSLOptions().setSni(sni);
    return this;
  }

  /**
   * @return whether the server uses the HA Proxy protocol
   */
  public boolean isUseProxyProtocol() { return useProxyProtocol; }

  /**
   * Set whether the server uses the HA Proxy protocol
   *
   * @return a reference to this, so the API can be used fluently
   */
  public NetServerOptions setUseProxyProtocol(boolean useProxyProtocol) {
    this.useProxyProtocol = useProxyProtocol;
    return this;
  }

  /**
   * @return the Proxy protocol timeout, in time unit specified by {@link #getProxyProtocolTimeoutUnit()}.
   */
  public long getProxyProtocolTimeout() {
    return proxyProtocolTimeout;
  }

  /**
   * Set the Proxy protocol timeout, default time unit is seconds.
   *
   * @param proxyProtocolTimeout the Proxy protocol timeout to set
   * @return a reference to this, so the API can be used fluently
   */
  public NetServerOptions setProxyProtocolTimeout(long proxyProtocolTimeout) {
    if (proxyProtocolTimeout < 0) {
      throw new IllegalArgumentException("proxyProtocolTimeout must be >= 0");
    }
    this.proxyProtocolTimeout = proxyProtocolTimeout;
    return this;
  }

  /**
   * Set the Proxy protocol timeout unit. If not specified, default is seconds.
   *
   * @param proxyProtocolTimeoutUnit specify time unit.
   * @return a reference to this, so the API can be used fluently
   */
  public NetServerOptions setProxyProtocolTimeoutUnit(TimeUnit proxyProtocolTimeoutUnit) {
    this.proxyProtocolTimeoutUnit = proxyProtocolTimeoutUnit;
    return this;
  }

  /**
   * @return the Proxy protocol timeout unit.
   */
  public TimeUnit getProxyProtocolTimeoutUnit() {
    return proxyProtocolTimeoutUnit;
  }

  /**
   * @return traffic shaping options used by Net server.
   */
  public TrafficShapingOptions getTrafficShapingOptions() {
    return this.trafficShapingOptions;
  }

  /**
   * Set traffic shaping options. If not specified, traffic is unthrottled.
   *
   * @param trafficShapingOptions options used by traffic handler
   * @return a reference to this, so the API can be used fluently
   */
  public NetServerOptions setTrafficShapingOptions(TrafficShapingOptions trafficShapingOptions) {
    this.trafficShapingOptions = trafficShapingOptions;
    return this;
  }

  private void init() {
    this.port = DEFAULT_PORT;
    this.host = DEFAULT_HOST;
    this.acceptBacklog = DEFAULT_ACCEPT_BACKLOG;
    this.useProxyProtocol = DEFAULT_USE_PROXY_PROTOCOL;
    this.proxyProtocolTimeout = DEFAULT_PROXY_PROTOCOL_TIMEOUT;
    this.proxyProtocolTimeoutUnit = DEFAULT_PROXY_PROTOCOL_TIMEOUT_TIME_UNIT;
    this.registerWriteHandler = DEFAULT_REGISTER_WRITE_HANDLER;
    this.quicOptions = new QuicOptions();
  }

  /**
   * @return {@code true} if a write-handler should be registered on the {@link io.vertx.core.eventbus.EventBus}, otherwise {@code false}
   */
  public boolean isRegisterWriteHandler() {
    return registerWriteHandler;
  }

  /**
   * Whether a write-handler should be registered on the {@link io.vertx.core.eventbus.EventBus}.
   * <p>
   * Defaults to {@code false}.
   *
   * @param registerWriteHandler true to register a write-handler
   * @return a reference to this, so the API can be used fluently
   * @see NetSocket#writeHandlerID()
   */
  public NetServerOptions setRegisterWriteHandler(boolean registerWriteHandler) {
    this.registerWriteHandler = registerWriteHandler;
    return this;
  }

  /**
   * @return whether the server should use file region
   */
  @GenIgnore
  public boolean isFileRegionEnabled() {
    return true;
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
  public NetServerOptions setQuicOptions(QuicOptions quicOptions) {
    this.quicOptions = quicOptions;
    return this;
  }
}
