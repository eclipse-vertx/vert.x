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

/**
 * Options for configuring a {@link io.vertx.core.net.NetServer}.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
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
  public static final int DEFAULT_ACCEPT_BACKLOG = 1024;

  /**
   * Default value of whether client auth is required (SSL/TLS) = false
   */
  public static final boolean DEFAULT_CLIENT_AUTH_REQUIRED = false;

  private int port;
  private String host;
  private int acceptBacklog;
  private boolean clientAuthRequired;

  /**
   * Default constructor
   */
  public NetServerOptions() {
    super();
    this.port = DEFAULT_PORT;
    this.host = DEFAULT_HOST;
    this.acceptBacklog = DEFAULT_ACCEPT_BACKLOG;
    this.clientAuthRequired = DEFAULT_CLIENT_AUTH_REQUIRED;
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
    this.clientAuthRequired = other.isClientAuthRequired();
  }

  /**
   * Create some options from JSON
   *
   * @param json  the JSON
   */
  public NetServerOptions(JsonObject json) {
    super(json);
    this.port = json.getInteger("port", DEFAULT_PORT);
    this.host = json.getString("host", DEFAULT_HOST);
    this.acceptBacklog = json.getInteger("acceptBacklog", DEFAULT_ACCEPT_BACKLOG);
    this.clientAuthRequired = json.getBoolean("clientAuthRequired", DEFAULT_CLIENT_AUTH_REQUIRED);
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
  public NetServerOptions setSsl(boolean ssl) {
    super.setSsl(ssl);
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
  public NetServerOptions setTrustStoreOptions(JksOptions options) {
    super.setTrustStoreOptions(options);
    return this;
  }

  @Override
  public NetServerOptions setPfxCaOptions(PfxOptions options) {
    return (NetServerOptions) super.setPfxCaOptions(options);
  }

  @Override
  public NetServerOptions setPemCaOptions(PemCaOptions options) {
    return (NetServerOptions) super.setPemCaOptions(options);
  }

  @Override
  public NetServerOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
    return this;
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
  public boolean isClientAuthRequired() {
    return clientAuthRequired;
  }

  /**
   * Set whether client auth is required
   *
   * @param clientAuthRequired  true if client auth is required
   * @return a reference to this, so the API can be used fluently
   */
  public NetServerOptions setClientAuthRequired(boolean clientAuthRequired) {
    this.clientAuthRequired = clientAuthRequired;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NetServerOptions)) return false;
    if (!super.equals(o)) return false;

    NetServerOptions that = (NetServerOptions) o;

    if (acceptBacklog != that.acceptBacklog) return false;
    if (clientAuthRequired != that.clientAuthRequired) return false;
    if (port != that.port) return false;
    if (host != null ? !host.equals(that.host) : that.host != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + port;
    result = 31 * result + (host != null ? host.hashCode() : 0);
    result = 31 * result + acceptBacklog;
    result = 31 * result + (clientAuthRequired ? 1 : 0);
    return result;
  }
}
