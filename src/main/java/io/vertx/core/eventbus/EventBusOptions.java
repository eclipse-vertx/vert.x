/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@DataObject(generateConverter = true, inheritConverter = true)
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

  private JksOptions trustStoreOptions;
  private JksOptions keystoreOptions;


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

    setKeyStoreOptions(other.keystoreOptions);
    setTrustStoreOptions(other.trustStoreOptions);
  }

  public EventBusOptions(JsonObject json) {
    this();

    EventBusOptionsConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    EventBusOptionsConverter.toJson(this, json);
    return json;
  }

  public ClientAuth getClientAuth() {
    return clientAuth;
  }

  public EventBusOptions setClientAuth(ClientAuth clientAuth) {
    this.clientAuth = clientAuth;
    return this;
  }

  public int getAcceptBacklog() {
    return acceptBacklog;
  }

  public EventBusOptions setAcceptBacklog(int acceptBacklog) {
    this.acceptBacklog = acceptBacklog;
    return this;
  }

  public String getHost() {
    return host;
  }

  public EventBusOptions setHost(String host) {
    this.host = host;
    return this;
  }

  public int getPort() {
    return port;
  }

  public EventBusOptions setPort(int port) {
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("clusterPort p must be in range 0 <= p <= 65535");
    }
    this.port = port;
    return this;
  }

  public int getReconnectAttempts() {
    return reconnectAttempts;
  }

  public EventBusOptions setReconnectAttempts(int reconnectAttempts) {
    this.reconnectAttempts = reconnectAttempts;
    return this;
  }

  public long getReconnectInterval() {
    return reconnectInterval;
  }

  public EventBusOptions setReconnectInterval(long reconnectInterval) {
    this.reconnectInterval = reconnectInterval;
    return this;
  }

  /**
   * Add a CRL path
   *
   * @param crlPath the path
   * @return a reference to this, so the API can be used fluently
   * @throws NullPointerException
   */
  @Override
  public EventBusOptions addCrlPath(String crlPath) throws NullPointerException {
    super.addCrlPath(crlPath);
    return this;
  }

  /**
   * Add a CRL value
   *
   * @param crlValue the value
   * @return a reference to this, so the API can be used fluently
   * @throws NullPointerException
   */
  @Override
  public EventBusOptions addCrlValue(Buffer crlValue) throws NullPointerException {
    super.addCrlValue(crlValue);
    return this;
  }

  /**
   * Add an enabled cipher suite
   *
   * @param suite the suite
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public EventBusOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
    return this;
  }

  /**
   * Set the idle timeout, in seconds. zero means don't timeout.
   * This determines if a connection will timeout and be closed if no data is received within the timeout.
   *
   * @param idleTimeout the timeout, in seconds
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public EventBusOptions setIdleTimeout(int idleTimeout) {
    super.setIdleTimeout(idleTimeout);
    return this;
  }

  /**
   * Set the key/cert options in jks format, aka Java keystore.
   *
   * @param options the key store in jks format
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public EventBusOptions setKeyStoreOptions(JksOptions options) {
    super.setKeyStoreOptions(options);
    this.keystoreOptions = options;
    return this;
  }

  public JksOptions getKeystoreOptions() {
    return keystoreOptions;
  }

  /**
   * Set the key/cert store options in pem format.
   *
   * @param options the options in pem format
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public EventBusOptions setPemKeyCertOptions(PemKeyCertOptions options) {
    super.setPemKeyCertOptions(options);
    return this;
  }

  /**
   * Set the trust options in pem format
   *
   * @param options the options in pem format
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public EventBusOptions setPemTrustOptions(PemTrustOptions options) {
    super.setPemTrustOptions(options);
    return this;
  }

  /**
   * Set the key/cert options in pfx format.
   *
   * @param options the key cert options in pfx format
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public EventBusOptions setPfxKeyCertOptions(PfxOptions options) {
    super.setPfxKeyCertOptions(options);
    return this;
  }

  /**
   * Set the trust options in pfx format
   *
   * @param options the options in pfx format
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public EventBusOptions setPfxTrustOptions(PfxOptions options) {
    super.setPfxTrustOptions(options);
    return this;
  }

  /**
   * Set whether SO_linger keep alive is enabled
   *
   * @param soLinger true if SO_linger is enabled
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public EventBusOptions setSoLinger(int soLinger) {
    super.setSoLinger(soLinger);
    return this;
  }

  /**
   * Set whether SSL/TLS is enabled
   *
   * @param ssl true if enabled
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public EventBusOptions setSsl(boolean ssl) {
    super.setSsl(ssl);
    return this;
  }

  /**
   * Set whether TCP keep alive is enabled
   *
   * @param tcpKeepAlive true if TCP keep alive is enabled
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public EventBusOptions setTcpKeepAlive(boolean tcpKeepAlive) {
    super.setTcpKeepAlive(tcpKeepAlive);
    return this;
  }

  /**
   * Set whether TCP no delay is enabled
   *
   * @param tcpNoDelay true if TCP no delay is enabled (Nagle disabled)
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public EventBusOptions setTcpNoDelay(boolean tcpNoDelay) {
    super.setTcpNoDelay(tcpNoDelay);
    return this;
  }

  /**
   * Set the trust options in jks format, aka Java trustore
   *
   * @param options the options in jks format
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public EventBusOptions setTrustStoreOptions(JksOptions options) {
    this.trustStoreOptions = options;
    super.setTrustStoreOptions(options);
    return this;
  }

  public JksOptions getTrustStoreOptions() {
    return trustStoreOptions;
  }

  /**
   * Set whether Netty pooled buffers are enabled
   *
   * @param usePooledBuffers true if pooled buffers enabled
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public EventBusOptions setUsePooledBuffers(boolean usePooledBuffers) {
    super.setUsePooledBuffers(usePooledBuffers);
    return this;
  }

  /**
   * Set the TCP receive buffer size
   *
   * @param receiveBufferSize the buffers size, in bytes
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public NetworkOptions setReceiveBufferSize(int receiveBufferSize) {
    super.setReceiveBufferSize(receiveBufferSize);
    return this;
  }

  /**
   * Set the value of reuse address
   *
   * @param reuseAddress the value of reuse address
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public NetworkOptions setReuseAddress(boolean reuseAddress) {
    super.setReuseAddress(reuseAddress);
    return this;
  }

  /**
   * Set the TCP send buffer size
   *
   * @param sendBufferSize the buffers size, in bytes
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public NetworkOptions setSendBufferSize(int sendBufferSize) {
    super.setSendBufferSize(sendBufferSize);
    return this;
  }

  /**
   * Set the value of traffic class
   *
   * @param trafficClass the value of traffic class
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public NetworkOptions setTrafficClass(int trafficClass) {
    super.setTrafficClass(trafficClass);
    return this;
  }

  public boolean isClustered() {
    return clustered;
  }

  public EventBusOptions setClustered(boolean clustered) {
    this.clustered = clustered;
    return this;
  }

  public EventBusOptions setTrustAll(boolean trustAll) {
    this.trustAll = trustAll;
    return this;
  }

  public boolean isTrustAll() {
    return trustAll;
  }

  public int getConnectTimeout() {
    return connectTimeout;
  }

  public EventBusOptions setConnectTimeout(int connectTimeout) {
    this.connectTimeout = connectTimeout;
    return this;
  }

  public long getClusterPingInterval() {
    return clusterPingInterval;
  }

  public EventBusOptions setClusterPingInterval(long clusterPingInterval) {
    if (clusterPingInterval < 1) {
      throw new IllegalArgumentException("clusterPingInterval must be greater than 0");
    }
    this.clusterPingInterval = clusterPingInterval;
    return this;
  }

  public long getClusterPingReplyInterval() {
    return clusterPingReplyInterval;
  }

  public EventBusOptions setClusterPingReplyInterval(long clusterPingReplyInterval) {
    if (clusterPingReplyInterval < 1) {
      throw new IllegalArgumentException("clusterPingReplyInterval must be greater than 0");
    }
    this.clusterPingReplyInterval = clusterPingReplyInterval;
    return this;
  }

  public String getClusterPublicHost() {
    return clusterPublicHost;
  }

  public EventBusOptions setClusterPublicHost(String clusterPublicHost) {
    this.clusterPublicHost = clusterPublicHost;
    return this;
  }

  public int getClusterPublicPort() {
    return clusterPublicPort;
  }

  public EventBusOptions setClusterPublicPort(int clusterPublicPort) {
    if (clusterPublicPort < 0 || clusterPublicPort > 65535) {
      throw new IllegalArgumentException("clusterPublicPort p must be in range 0 <= p <= 65535");
    }
    this.clusterPublicPort = clusterPublicPort;
    return this;
  }
}
