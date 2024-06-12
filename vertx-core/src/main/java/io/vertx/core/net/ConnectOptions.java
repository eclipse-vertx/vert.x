/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
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

/**
 * Options for configuring how to connect to a TCP server.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class ConnectOptions {

  /**
   * SSL enable by default = false
   */
  public static final boolean DEFAULT_SSL = false;

  private String host;
  private Integer port;
  private String sniServerName;
  private SocketAddress remoteAddress;
  private ProxyOptions proxyOptions;
  private boolean ssl;
  private ClientSSLOptions sslOptions;
  private int timeout;

   /**
    * The default constructor
    */
  public ConnectOptions() {
    host = null;
    port = null;
    sniServerName = null;
    remoteAddress = null;
    proxyOptions = null;
    ssl = DEFAULT_SSL;
    sslOptions = null;
    timeout = -1;
  }

   /**
    * Copy constructor
    *
    * @param other  the options to copy
    */
   public ConnectOptions(ConnectOptions other) {
     host = other.getHost();
     port = other.getPort();
     sniServerName = other.getSniServerName();
     remoteAddress = other.getRemoteAddress();
     proxyOptions = other.getProxyOptions() != null ? new ProxyOptions(other.getProxyOptions()) : null;
     ssl = other.isSsl();
     sslOptions = other.getSslOptions() != null ? new ClientSSLOptions(other.getSslOptions()) : null;
     timeout = other.getTimeout();
   }

  /**
   * Get the host name to be used by the client connection.
   *
   * @return  the host name
   */
  public String getHost() {
    return host;
  }

  /**
   * Set the host name to be used by the client connection.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public ConnectOptions setHost(String host) {
    this.host = host;
    return this;
  }

  /**
   * Get the port to be used by the client connection.
   *
   * @return  the port
   */
  public Integer getPort() {
    return port;
  }

  /**
   * Set the port to be used by the client connection.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public ConnectOptions setPort(Integer port) {
    this.port = port;
    return this;
  }

  /**
   * Get the remote address to connect to, if none is provided {@link #host}/{@link #port} wille be used.
   *
   * @return the remote address
   */
  public SocketAddress getRemoteAddress() {
    return remoteAddress;
  }

  /**
   * Set the remote address to be used by the client connection.
   *
   * <p> When the server address is {@code null}, the address will be resolved after the {@link #host}
   * property by the Vert.x resolver and the {@link #port} will be used.
   *
   * <p> Use this when you want to connect to a specific server address without name resolution or use a domain socket.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public ConnectOptions setRemoteAddress(SocketAddress remoteAddress) {
    this.remoteAddress = remoteAddress;
    return this;
  }

  /**
   * @return the SNI (server name indication) server name
   */
  public String getSniServerName() {
    return sniServerName;
  }

  /**
   * Set the SNI server name to use.
   *
   * @param sniServerName the server name
   * @return a reference to this, so the API can be used fluently
   */
  public ConnectOptions setSniServerName(String sniServerName) {
    this.sniServerName = sniServerName;
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
   * Set proxy options for connections via CONNECT proxy (e.g. Squid) or a SOCKS proxy.
   * <p>
   * When none is provided, the {@link NetClientOptions} proxy options will be used instead.
   *
   * @param proxyOptions proxy options object
   * @return a reference to this, so the API can be used fluently
   */
  public ConnectOptions setProxyOptions(ProxyOptions proxyOptions) {
    this.proxyOptions = proxyOptions;
    return this;
  }

  /**
   * @return is SSL/TLS enabled?
   */
  public boolean isSsl() {
    return ssl;
  }

  /**
   * Set whether SSL/TLS is enabled.
   *
   * @param ssl {@code true} if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public ConnectOptions setSsl(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  /**
   * @return the SSL options
   */
  public ClientSSLOptions getSslOptions() {
    return sslOptions;
  }

  /**
   * Set the SSL options to use.
   * <p>
   * When none is provided, the {@link NetClientOptions} SSL options will be used instead.
   * @param sslOptions the SSL options to use
   * @return a reference to this, so the API can be used fluently
   */
  public ConnectOptions setSslOptions(ClientSSLOptions sslOptions) {
    this.sslOptions = sslOptions;
    return this;
  }

  /**
   * @return the value of connect timeout in millis or {@code -1} when using the client defined connect timeout {@link NetClientOptions#getConnectTimeout()}
   */
  public int getTimeout() {
    return timeout;
  }

  /**
   * Override the client connect timeout in millis when {@code timeout >= 0} or use the client defined connect timeout {@link NetClientOptions#getConnectTimeout()}
   * when {@code timeout == -1}.
   *
   * @param timeout connect timeout, in ms
   * @return a reference to this, so the API can be used fluently
   */
  public ConnectOptions setTimeout(int timeout) {
    if (timeout < -2) {
      throw new IllegalArgumentException("connectTimeout must be >= 0 or -1 (use default client value)");
    }
    this.timeout = timeout;
    return this;
  }
}
