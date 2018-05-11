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

package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;

import java.util.Set;

/**
 * Options to configure the event bus.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@DataObject(generateConverter = true, inheritConverter = true, publicConverter = false)
public class EventBusOptions extends TCPSSLOptions {

  private boolean clustered = VertxOptions.DEFAULT_CLUSTERED;
  private String clusterPublicHost = VertxOptions.DEFAULT_CLUSTER_PUBLIC_HOST;
  private int clusterPublicPort = VertxOptions.DEFAULT_CLUSTER_PUBLIC_PORT;
  private long clusterPingInterval = VertxOptions.DEFAULT_CLUSTER_PING_INTERVAL;
  private long clusterPingReplyInterval = VertxOptions.DEFAULT_CLUSTER_PING_REPLY_INTERVAL;

  // Attributes used to configure the server of the event bus when the event bus is clustered.

  /**
   * The default port to listen on = 0 (meaning a random ephemeral free port will be chosen)
   */
  public static final int DEFAULT_PORT = VertxOptions.DEFAULT_CLUSTER_PORT;

  /**
   * The default host to listen on = "0.0.0.0" (meaning listen on all available interfaces).
   */
  public static final String DEFAULT_HOST = VertxOptions.DEFAULT_CLUSTER_HOST;

  /**
   * The default accept backlog = 1024
   */
  public static final int DEFAULT_ACCEPT_BACKLOG = -1;

  /**
   * Default value of whether client auth is required (SSL/TLS) = No
   */
  public static final ClientAuth DEFAULT_CLIENT_AUTH = ClientAuth.NONE;

  private int port;
  private String host;
  private int acceptBacklog;
  private ClientAuth clientAuth = DEFAULT_CLIENT_AUTH;

  // Attributes used to configure the client of the event bus when the event bus is clustered.

  /**
   * The default value for reconnect attempts = 0
   */
  public static final int DEFAULT_RECONNECT_ATTEMPTS = 0;

  /**
   * The default value for reconnect interval = 1000 ms
   */
  public static final long DEFAULT_RECONNECT_INTERVAL = 1000;

  /**
   * The default value of connect timeout = 60000 ms
   */
  public static final int DEFAULT_CONNECT_TIMEOUT = 60 * 1000;

  /**
   * The default value of whether all servers (SSL/TLS) should be trusted = true
   */
  public static final boolean DEFAULT_TRUST_ALL = true;

  private int reconnectAttempts;
  private long reconnectInterval;

  private int connectTimeout;
  private boolean trustAll;

  /**
   * Creates a new instance of {@link EventBusOptions} using the default configuration.
   */
  public EventBusOptions() {
    super();

    clustered = VertxOptions.DEFAULT_CLUSTERED;

    port = DEFAULT_PORT;
    host = DEFAULT_HOST;
    acceptBacklog = DEFAULT_ACCEPT_BACKLOG;
    clientAuth = DEFAULT_CLIENT_AUTH;

    reconnectAttempts = DEFAULT_RECONNECT_ATTEMPTS;
    reconnectInterval = DEFAULT_RECONNECT_INTERVAL;

    connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    trustAll = DEFAULT_TRUST_ALL;
  }

  /**
   * Copy constructor to create an instance of {@link EventBusOptions} using the values of the given object.
   *
   * @param other the other {@link EventBusOptions}
   */
  public EventBusOptions(EventBusOptions other) {
    super(other);

    this.clustered = other.clustered;
    this.clusterPublicHost = other.clusterPublicHost;
    this.clusterPublicPort = other.clusterPublicPort;
    this.clusterPingInterval = other.clusterPingInterval;
    this.clusterPingReplyInterval = other.clusterPingReplyInterval;

    this.port = other.port;
    this.host = other.host;
    this.acceptBacklog = other.acceptBacklog;
    this.clientAuth = other.clientAuth;

    this.reconnectInterval = other.reconnectInterval;
    this.reconnectAttempts = other.reconnectAttempts;
    this.connectTimeout = other.connectTimeout;
    this.trustAll = other.trustAll;
  }

  /**
   * Creates a new instance of {@link EventBusOptions} from the JSON object. This JSOn object has (generally)
   * be generated using {@link #toJson()}.
   *
   * @param json the json object
   */
  public EventBusOptions(JsonObject json) {
    this();

    EventBusOptionsConverter.fromJson(json, this);
  }

  /**
   * Builds a JSON object representing the current {@link EventBusOptions}.
   *
   * @return the JSON representation
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    EventBusOptionsConverter.toJson(this, json);
    return json;
  }

  /**
   * @return the configure client authentication requirement
   * @see NetServerOptions#getClientAuth()
   */
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
   * @see NetServerOptions#setClientAuth(ClientAuth)
   */
  public EventBusOptions setClientAuth(ClientAuth clientAuth) {
    this.clientAuth = clientAuth;
    return this;
  }

  /**
   * @return the value of accept backlog.
   * @see NetServerOptions#getAcceptBacklog()
   */
  public int getAcceptBacklog() {
    return acceptBacklog;
  }

  /**
   * Set the accept back log.
   *
   * @param acceptBacklog accept backlog
   * @return a reference to this, so the API can be used fluently
   * @see NetServerOptions#setAcceptBacklog(int)
   */
  public EventBusOptions setAcceptBacklog(int acceptBacklog) {
    this.acceptBacklog = acceptBacklog;
    return this;
  }

  /**
   * @return the host, which can be configured from the {@link VertxOptions#setClusterHost(String)}, or using
   * the {@code --cluster-host} command line option.
   * @see NetServerOptions#getHost()
   */
  public String getHost() {
    return host;
  }

  /**
   * Sets the host.
   *
   * @param host the host
   * @return a reference to this, so the API can be used fluently
   * @see NetServerOptions#setHost(String)
   */
  public EventBusOptions setHost(String host) {
    this.host = host;
    return this;
  }

  /**
   * @return the port, which can be configured from the {@link VertxOptions#setClusterPort(int)}, or
   * using the {@code --cluster-port} command line option.
   * @see NetServerOptions#getPort()
   */
  public int getPort() {
    return port;
  }

  /**
   * Sets the port.
   *
   * @param port the port
   * @return a reference to this, so the API can be used fluently
   * @see NetServerOptions#setPort(int)
   */
  public EventBusOptions setPort(int port) {
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("clusterPort p must be in range 0 <= p <= 65535");
    }
    this.port = port;
    return this;
  }


  /**
   * @return the value of reconnect attempts
   * @see NetClientOptions#getReconnectAttempts()
   */
  public int getReconnectAttempts() {
    return reconnectAttempts;
  }


  /**
   * Sets the value of reconnect attempts.
   *
   * @param attempts the maximum number of reconnect attempts
   * @return a reference to this, so the API can be used fluently
   * @see NetClientOptions#setReconnectAttempts(int)
   */
  public EventBusOptions setReconnectAttempts(int attempts) {
    this.reconnectAttempts = attempts;
    return this;
  }

  /**
   * @return the value of reconnect interval
   * @see NetClientOptions#getReconnectInterval()
   */
  public long getReconnectInterval() {
    return reconnectInterval;
  }

  /**
   * Set the reconnect interval.
   *
   * @param interval the reconnect interval in ms
   * @return a reference to this, so the API can be used fluently
   * @see NetClientOptions#setReconnectInterval(long)
   */
  public EventBusOptions setReconnectInterval(long interval) {
    this.reconnectInterval = interval;
    return this;
  }

  @Override
  public EventBusOptions addCrlPath(String crlPath) throws NullPointerException {
    super.addCrlPath(crlPath);
    return this;
  }

  @Override
  public EventBusOptions addCrlValue(Buffer crlValue) throws NullPointerException {
    super.addCrlValue(crlValue);
    return this;
  }

  @Override
  public EventBusOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
    return this;
  }

  @Override
  public EventBusOptions setIdleTimeout(int idleTimeout) {
    super.setIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  @GenIgnore
  public EventBusOptions setKeyCertOptions(KeyCertOptions options) {
    super.setKeyCertOptions(options);
    return this;
  }

  @Override
  public EventBusOptions setKeyStoreOptions(JksOptions options) {
    super.setKeyStoreOptions(options);
    return this;
  }

  @Override
  public EventBusOptions setPemKeyCertOptions(PemKeyCertOptions options) {
    super.setPemKeyCertOptions(options);
    return this;
  }

  @Override
  public EventBusOptions setPemTrustOptions(PemTrustOptions options) {
    super.setPemTrustOptions(options);
    return this;
  }

  @Override
  public EventBusOptions setPfxKeyCertOptions(PfxOptions options) {
    super.setPfxKeyCertOptions(options);
    return this;
  }

  @Override
  public EventBusOptions setPfxTrustOptions(PfxOptions options) {
    super.setPfxTrustOptions(options);
    return this;
  }

  @Override
  public EventBusOptions setSoLinger(int soLinger) {
    super.setSoLinger(soLinger);
    return this;
  }

  @Override
  public EventBusOptions setSsl(boolean ssl) {
    super.setSsl(ssl);
    return this;
  }

  @Override
  public EventBusOptions setTcpKeepAlive(boolean tcpKeepAlive) {
    super.setTcpKeepAlive(tcpKeepAlive);
    return this;
  }

  @Override
  public EventBusOptions setTcpNoDelay(boolean tcpNoDelay) {
    super.setTcpNoDelay(tcpNoDelay);
    return this;
  }

  @Override
  public EventBusOptions setTrustOptions(TrustOptions options) {
    super.setTrustOptions(options);
    return this;
  }

  @Override
  public EventBusOptions setTrustStoreOptions(JksOptions options) {
    super.setTrustStoreOptions(options);
    return this;
  }

  @Override
  public EventBusOptions setUsePooledBuffers(boolean usePooledBuffers) {
    super.setUsePooledBuffers(usePooledBuffers);
    return this;
  }

  @Override
  public EventBusOptions setReceiveBufferSize(int receiveBufferSize) {
    super.setReceiveBufferSize(receiveBufferSize);
    return this;
  }

  @Override
  public EventBusOptions setReuseAddress(boolean reuseAddress) {
    super.setReuseAddress(reuseAddress);
    return this;
  }

  @Override
  public EventBusOptions setReusePort(boolean reusePort) {
    super.setReusePort(reusePort);
    return this;
  }

  @Override
  public EventBusOptions setSendBufferSize(int sendBufferSize) {
    super.setSendBufferSize(sendBufferSize);
    return this;
  }

  @Override
  public EventBusOptions setTrafficClass(int trafficClass) {
    super.setTrafficClass(trafficClass);
    return this;
  }

  @Override
  public EventBusOptions setUseAlpn(boolean useAlpn) {
    return (EventBusOptions) super.setUseAlpn(useAlpn);
  }

  @Override
  public EventBusOptions setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
    return (EventBusOptions) super.setSslEngineOptions(sslEngineOptions);
  }

  @Override
  public EventBusOptions setJdkSslEngineOptions(JdkSSLEngineOptions sslEngineOptions) {
    return (EventBusOptions) super.setJdkSslEngineOptions(sslEngineOptions);
  }

  @Override
  public EventBusOptions setOpenSslEngineOptions(OpenSSLEngineOptions sslEngineOptions) {
    return (EventBusOptions) super.setOpenSslEngineOptions(sslEngineOptions);
  }

  @Override
  public EventBusOptions setEnabledSecureTransportProtocols(Set<String> enabledSecureTransportProtocols) {
    return (EventBusOptions) super.setEnabledSecureTransportProtocols(enabledSecureTransportProtocols);
  }

  @Override
  public EventBusOptions addEnabledSecureTransportProtocol(String protocol) {
    return (EventBusOptions) super.addEnabledSecureTransportProtocol(protocol);
  }

  @Override
  public EventBusOptions removeEnabledSecureTransportProtocol(String protocol) {
    return (EventBusOptions) super.removeEnabledSecureTransportProtocol(protocol);
  }

  @Override
  public EventBusOptions setTcpFastOpen(boolean tcpFastOpen) {
    return (EventBusOptions) super.setTcpFastOpen(tcpFastOpen);
  }

  @Override
  public EventBusOptions setTcpCork(boolean tcpCork) {
    return (EventBusOptions) super.setTcpCork(tcpCork);
  }

  @Override
  public EventBusOptions setTcpQuickAck(boolean tcpQuickAck) {
    return (EventBusOptions) super.setTcpQuickAck(tcpQuickAck);
  }

  @Override
  public EventBusOptions setLogActivity(boolean logEnabled) {
    return (EventBusOptions) super.setLogActivity(logEnabled);
  }

  /**
   * @return whether or not the event bus is clustered. This can be configured from the
   * {@link VertxOptions#setClustered(boolean)} method or from the {@code --cluster} option from the command
   * line.
   */
  public boolean isClustered() {
    return clustered;
  }

  /**
   * Sets whether or not the event bus is clustered.
   *
   * @param clustered {@code true} to start the event bus as a clustered event bus.
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusOptions setClustered(boolean clustered) {
    this.clustered = clustered;
    return this;
  }

  /**
   * Set whether all server certificates should be trusted.
   *
   * @param trustAll true if all should be trusted
   * @return a reference to this, so the API can be used fluently
   * @see NetClientOptions#setTrustAll(boolean)
   */
  public EventBusOptions setTrustAll(boolean trustAll) {
    this.trustAll = trustAll;
    return this;
  }

  /**
   * @return true if all server certificates should be trusted
   * @see NetClientOptions#isTrustAll()
   */
  public boolean isTrustAll() {
    return trustAll;
  }

  /**
   * @return the value of connect timeout
   * @see NetClientOptions#getConnectTimeout()
   */
  public int getConnectTimeout() {
    return connectTimeout;
  }

  /**
   * Sets the connect timeout
   *
   * @param connectTimeout connect timeout, in ms
   * @return a reference to this, so the API can be used fluently
   * @see NetClientOptions#setConnectTimeout(int)
   */
  public EventBusOptions setConnectTimeout(int connectTimeout) {
    this.connectTimeout = connectTimeout;
    return this;
  }

  /**
   * Get the value of cluster ping reply interval, in ms.
   * After sending a ping, if a pong is not received in this time, the node will be considered dead.
   * <p>
   * The value can be configured from {@link VertxOptions#setClusterPingInterval(long)}.
   *
   * @return the value of cluster ping reply interval
   */
  public long getClusterPingInterval() {
    return clusterPingInterval;
  }

  /**
   * Set the value of cluster ping interval, in ms.
   *
   * @param clusterPingInterval The value of cluster ping interval, in ms.
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusOptions setClusterPingInterval(long clusterPingInterval) {
    if (clusterPingInterval < 1) {
      throw new IllegalArgumentException("clusterPingInterval must be greater than 0");
    }
    this.clusterPingInterval = clusterPingInterval;
    return this;
  }

  /**
   * Get the value of cluster ping reply interval, in ms.
   * After sending a ping, if a pong is not received in this time, the node will be considered dead.
   * <p>
   * The value can be configured from {@link VertxOptions#setClusterPingReplyInterval(long)}}.
   *
   * @return the value of cluster ping reply interval
   */
  public long getClusterPingReplyInterval() {
    return clusterPingReplyInterval;
  }

  /**
   * Set the value of cluster ping reply interval, in ms.
   *
   * @param clusterPingReplyInterval The value of cluster ping reply interval, in ms.
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusOptions setClusterPingReplyInterval(long clusterPingReplyInterval) {
    if (clusterPingReplyInterval < 1) {
      throw new IllegalArgumentException("clusterPingReplyInterval must be greater than 0");
    }
    this.clusterPingReplyInterval = clusterPingReplyInterval;
    return this;
  }

  /**
   * Get the public facing port to be used when clustering.
   * <p>
   * It can be configured using {@link VertxOptions#setClusterPublicHost(String)}
   *
   * @return the public facing port
   */
  public String getClusterPublicHost() {
    return clusterPublicHost;
  }

  /**
   * Set the public facing hostname to be used for clustering.
   * Sometimes, e.g. when running on certain clouds, the local address the server listens on for clustering is
   * not the same address that other nodes connect to it at, as the OS / cloud infrastructure does some kind of
   * proxying. If this is the case you can specify a public hostname which is different from the hostname the
   * server listens at.
   * <p>
   * The default value is null which means use the same as the cluster hostname.
   *
   * @param clusterPublicHost the public host name to use
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusOptions setClusterPublicHost(String clusterPublicHost) {
    this.clusterPublicHost = clusterPublicHost;
    return this;
  }

  /**
   * Gets the public facing port to be used when clustering.
   * <p>
   * This can be configured from {@link VertxOptions#setClusterPublicPort(int)}.
   *
   * @return the public facing port
   */
  public int getClusterPublicPort() {
    return clusterPublicPort;
  }

  /**
   * See {@link #setClusterPublicHost(String)} for an explanation.
   *
   * @param clusterPublicPort  the public port to use
   * @return a reference to this, so the API can be used fluently
   */
  public EventBusOptions setClusterPublicPort(int clusterPublicPort) {
    if (clusterPublicPort < 0 || clusterPublicPort > 65535) {
      throw new IllegalArgumentException("clusterPublicPort p must be in range 0 <= p <= 65535");
    }
    this.clusterPublicPort = clusterPublicPort;
    return this;
  }
}
