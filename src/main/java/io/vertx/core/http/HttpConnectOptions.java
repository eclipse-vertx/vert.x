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

package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.VertxException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.Address;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.SocketAddress;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Options describing how an {@link HttpClient} will connect to a server.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class HttpConnectOptions {

  /**
   * The default value for proxy options = {@code null}
   */
  public static final ProxyOptions DEFAULT_PROXY_OPTIONS = null;

  /**
   * The default value for server method = {@code null}
   */
  public static final SocketAddress DEFAULT_SERVER = null;

  /**
   * The default value for host name = {@code null}
   */
  public static final String DEFAULT_HOST = null;

  /**
   * The default value for port = {@code null}
   */
  public static final Integer DEFAULT_PORT = null;

  /**
   * The default value for SSL = {@code null}
   */
  public static final Boolean DEFAULT_SSL = null;

  /**
   * The default connect timeout = {@code -1L} (disabled)
   */
  public static final long DEFAULT_CONNECT_TIMEOUT = -1L;

  private ProxyOptions proxyOptions;
  private Address server;
  private String host;
  private Integer port;
  private Boolean ssl;
  private ClientSSLOptions sslOptions;;
  private long connectTimeout;

  /**
   * Default constructor
   */
  public HttpConnectOptions() {
    init();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public HttpConnectOptions(HttpConnectOptions other) {
    init();
    setProxyOptions(other.proxyOptions);
    setServer(other.server);
    setHost(other.host);
    setPort(other.port);
    setSsl(other.ssl);
    sslOptions = other.sslOptions != null ? new ClientSSLOptions(other.sslOptions) : null;
    setConnectTimeout(other.connectTimeout);
  }

  /**
   * Create options from JSON
   *
   * @param json the JSON
   */
  public HttpConnectOptions(JsonObject json) {
    init();
    HttpConnectOptionsConverter.fromJson(json, this);
    JsonObject server = json.getJsonObject("server");
    if (server != null) {
      this.server = SocketAddress.fromJson(server);
    }
  }

  protected void init() {
    proxyOptions = DEFAULT_PROXY_OPTIONS;
    server = DEFAULT_SERVER;
    host = DEFAULT_HOST;
    port = DEFAULT_PORT;
    ssl = DEFAULT_SSL;
    sslOptions = null;
    connectTimeout = DEFAULT_CONNECT_TIMEOUT;
  }

  /**
   * Get the proxy options override for connections
   *
   * @return proxy options override
   */
  public ProxyOptions getProxyOptions() {
    return proxyOptions;
  }

  /**
   * Override the {@link HttpClientOptions#setProxyOptions(ProxyOptions)} proxy options
   * for connections.
   *
   * @param proxyOptions proxy options override object
   * @return a reference to this, so the API can be used fluently
   */
  public HttpConnectOptions setProxyOptions(ProxyOptions proxyOptions) {
    this.proxyOptions = proxyOptions;
    return this;
  }

  /**
   * Get the server address to be used by the client request.
   *
   * @return the server address
   */
  public Address getServer() {
    return server;
  }

  /**
   * Set the server address to be used by the client request.
   *
   * <p> When the server address is {@code null}, the address will be resolved after the {@code host}
   * property by the Vert.x resolver.
   *
   * <p> Use this when you want to connect to a specific server address without name resolution.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public HttpConnectOptions setServer(Address server) {
    this.server = server;
    return this;
  }

  /**
   * Get the host name to be used by the client request.
   *
   * @return  the host name
   */
  public String getHost() {
    return host;
  }

  /**
   * Set the host name to be used by the client request.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public HttpConnectOptions setHost(String host) {
    this.host = host;
    return this;
  }

  /**
   * Get the port to be used by the client request.
   *
   * @return  the port
   */
  public Integer getPort() {
    return port;
  }

  /**
   * Set the port to be used by the client request.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public HttpConnectOptions setPort(Integer port) {
    this.port = port;
    return this;
  }

  /**
   * @return is SSL/TLS enabled?
   */
  public Boolean isSsl() {
    return ssl;
  }

  /**
   * Set whether SSL/TLS is enabled.
   *
   * @param ssl  true if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public HttpConnectOptions setSsl(Boolean ssl) {
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
   * When none is provided, the client SSL options will be used instead.
   * @param sslOptions the SSL options to use
   * @return a reference to this, so the API can be used fluently
   */
  public HttpConnectOptions setSslOptions(ClientSSLOptions sslOptions) {
    this.sslOptions = sslOptions;
    return this;
  }

  /**
   * @return the amount of time after which, if the request is not obtained from the client within the timeout period,
   *         the {@code Future<HttpClientRequest>} obtained from the client is failed with a {@link java.util.concurrent.TimeoutException}
   */
  public long getConnectTimeout() {
    return connectTimeout;
  }

  /**
   * Sets the amount of time after which, if the request is not obtained from the client within the timeout period,
   * the {@code Future<HttpClientRequest>} obtained from the client is failed with a {@link java.util.concurrent.TimeoutException}.
   *
   * Note this is not related to the TCP {@link HttpClientOptions#setConnectTimeout(int)} option, when a request is made against
   * a pooled HTTP client, the timeout applies to the duration to obtain a connection from the pool to serve the request, the timeout
   * might fire because the server does not respond in time or the pool is too busy to serve a request.
   *
   * @param timeout the amount of time in milliseconds.
   * @return a reference to this, so the API can be used fluently
   */
  public HttpConnectOptions setConnectTimeout(long timeout) {
    this.connectTimeout = timeout;
    return this;
  }

  private URL parseUrl(String surl) {
    // Note - parsing a URL this way is slower than specifying host, port and relativeURI
    try {
      return new URL(surl);
    } catch (MalformedURLException e) {
      throw new VertxException("Invalid url: " + surl, e);
    }
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    HttpConnectOptionsConverter.toJson(this, json);
    Address serverAddr = this.server;
    if (serverAddr instanceof SocketAddress) {
      SocketAddress socketAddr = (SocketAddress) serverAddr;
      json.put("server", socketAddr.toJson());
    }
    return json;
  }
}
