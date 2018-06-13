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
import io.vertx.core.http.ClientAuth;
import io.vertx.core.json.JsonObject;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Options for configuring a {@link io.vertx.core.net.NetServer}.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true, publicConverter = false)
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
   * Default value of whether client auth is required (SSL/TLS) = No
   */
  public static final ClientAuth DEFAULT_CLIENT_AUTH = ClientAuth.NONE;

  /**
   * Default value of whether the server supports SNI = false
   */
  public static final boolean DEFAULT_SNI = false;

  private int port;
  private String host;
  private int acceptBacklog;
  private ClientAuth clientAuth;
  private boolean sni;

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
    this.clientAuth = other.getClientAuth();
    this.sni = other.isSni();
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
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = super.toJson();
    NetServerOptionsConverter.toJson(this, json);
    return json;
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
  public NetServerOptions setUsePooledBuffers(boolean usePooledBuffers) {
    super.setUsePooledBuffers(usePooledBuffers);
    return this;
  }

  @Override
  public NetServerOptions setIdleTimeout(int idleTimeout) {
    super.setIdleTimeout(idleTimeout);
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
  public NetServerOptions setJdkSslEngineOptions(JdkSSLEngineOptions sslEngineOptions) {
    return (NetServerOptions) super.setSslEngineOptions(sslEngineOptions);
  }

  @Override
  public NetServerOptions setOpenSslEngineOptions(OpenSSLEngineOptions sslEngineOptions) {
    return (NetServerOptions) super.setSslEngineOptions(sslEngineOptions);
  }

  @Override
  public NetServerOptions setKeyCertOptions(KeyCertOptions options) {
    super.setKeyCertOptions(options);
    return this;
  }

  @Override
  public NetServerOptions setKeyStoreOptions(JksOptions options) {
    super.setKeyStoreOptions(options);
    return this;
  }

  @Override
  public NetServerOptions setPfxKeyCertOptions(PfxOptions options) {
    return (NetServerOptions) super.setPfxKeyCertOptions(options);
  }

  @Override
  public NetServerOptions setPemKeyCertOptions(PemKeyCertOptions options) {
    return (NetServerOptions) super.setPemKeyCertOptions(options);
  }

  @Override
  public NetServerOptions setTrustOptions(TrustOptions options) {
    super.setTrustOptions(options);
    return this;
  }

  @Override
  public NetServerOptions setTrustStoreOptions(JksOptions options) {
    super.setTrustStoreOptions(options);
    return this;
  }

  @Override
  public NetServerOptions setPfxTrustOptions(PfxOptions options) {
    return (NetServerOptions) super.setPfxTrustOptions(options);
  }

  @Override
  public NetServerOptions setPemTrustOptions(PemTrustOptions options) {
    return (NetServerOptions) super.setPemTrustOptions(options);
  }

  @Override
  public NetServerOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
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
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("port p must be in range 0 <= p <= 65535");
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

  /**
   *
   * @return true if client auth is required
   */
  @Deprecated
  public boolean isClientAuthRequired() {
    return clientAuth == ClientAuth.REQUIRED;
  }

  /**
   * Set whether client auth is required
   *
   * @param clientAuthRequired  true if client auth is required
   * @return a reference to this, so the API can be used fluently
   */
  @Deprecated
  public NetServerOptions setClientAuthRequired(boolean clientAuthRequired) {
    this.clientAuth = clientAuthRequired ? ClientAuth.REQUIRED : ClientAuth.NONE;
    return this;
  }

  public ClientAuth getClientAuth() {
    return clientAuth;
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
    this.clientAuth = clientAuth;
    return this;
  }

  @Override
  public NetServerOptions setLogActivity(boolean logEnabled) {
    return (NetServerOptions) super.setLogActivity(logEnabled);
  }

  /**
   * @return whether the server supports Server Name Indication
   */
  public boolean isSni() {
    return sni;
  }

  /**
   * Set whether the server supports Server Name Indiciation
   *
   * @return a reference to this, so the API can be used fluently
   */
  public NetServerOptions setSni(boolean sni) {
    this.sni = sni;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NetServerOptions)) return false;
    if (!super.equals(o)) return false;

    NetServerOptions that = (NetServerOptions) o;

    if (acceptBacklog != that.acceptBacklog) return false;
    if (clientAuth != that.clientAuth) return false;
    if (port != that.port) return false;
    if (host != null ? !host.equals(that.host) : that.host != null) return false;
    if (sni != that.sni) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + port;
    result = 31 * result + (host != null ? host.hashCode() : 0);
    result = 31 * result + acceptBacklog;
    result = 31 * result + clientAuth.hashCode();
    result = 31 * result + (sni ? 1 : 0);
    return result;
  }

  private void init() {
    this.port = DEFAULT_PORT;
    this.host = DEFAULT_HOST;
    this.acceptBacklog = DEFAULT_ACCEPT_BACKLOG;
    this.clientAuth = DEFAULT_CLIENT_AUTH;
    this.sni = DEFAULT_SNI;
  }
}
