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

import io.vertx.codegen.annotations.Options;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class NetServerOptions extends TCPSSLOptions {

  // Server specific HTTP stuff

  public static final int DEFAULT_PORT = 0;
  public static final String DEFAULT_HOST = "0.0.0.0";
  public static final int DEFAULT_ACCEPT_BACKLOG = 1024;
  public static final boolean DEFAULT_CLIENT_AUTH_REQUIRED = false;

  private int port;
  private String host;
  private int acceptBacklog;
  private boolean clientAuthRequired;

  public NetServerOptions(NetServerOptions other) {
    super(other);
    this.port = other.getPort();
    this.host = other.getHost();
    this.acceptBacklog = other.getAcceptBacklog();
    this.clientAuthRequired = other.isClientAuthRequired();
  }

  public NetServerOptions(JsonObject json) {
    super(json);
    this.port = json.getInteger("port", DEFAULT_PORT);
    this.host = json.getString("host", DEFAULT_HOST);
    this.acceptBacklog = json.getInteger("acceptBacklog", DEFAULT_ACCEPT_BACKLOG);
    this.clientAuthRequired = json.getBoolean("clientAuthRequired", DEFAULT_CLIENT_AUTH_REQUIRED);
  }

  public NetServerOptions() {
    super();
    this.port = DEFAULT_PORT;
    this.host = DEFAULT_HOST;
    this.acceptBacklog = DEFAULT_ACCEPT_BACKLOG;
    this.clientAuthRequired = DEFAULT_CLIENT_AUTH_REQUIRED;
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
  public NetServerOptions setKeyStoreOptions(KeyStoreOptions keyStore) {
    super.setKeyStoreOptions(keyStore);
    return this;
  }

  @Override
  public NetServerOptions setTrustStoreOptions(TrustStoreOptions trustStore) {
    super.setTrustStoreOptions(trustStore);
    return this;
  }

  @Override
  public NetServerOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
    return this;
  }

  public int getAcceptBacklog() {
    return acceptBacklog;
  }

  public NetServerOptions setAcceptBacklog(int acceptBacklog) {
    this.acceptBacklog = acceptBacklog;
    return this;
  }

  public int getPort() {
    return port;
  }

  public NetServerOptions setPort(int port) {
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("port p must be in range 0 <= p <= 65535");
    }
    this.port = port;
    return this;
  }

  public String getHost() {
    return host;
  }

  public NetServerOptions setHost(String host) {
    this.host = host;
    return this;
  }

  public boolean isClientAuthRequired() {
    return clientAuthRequired;
  }

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
