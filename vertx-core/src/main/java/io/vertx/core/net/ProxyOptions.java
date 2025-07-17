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
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

import java.util.Objects;

/**
 * Proxy options for a net client or a net client.
 *
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class ProxyOptions {

  /**
   * The default proxy type (HTTP)
   */
  public static final ProxyType DEFAULT_TYPE = ProxyType.HTTP;

  /**
   * The default port for proxy connect = 3128
   * <p>
   * 3128 is the default port for Squid
   */
  public static final int DEFAULT_PORT = 3128;

  /**
   * The default hostname for proxy connect = "localhost"
   */
  public static final String DEFAULT_HOST = "localhost";

  /**
   * The default timeout in milliseconds for proxy connect = 10000
   */
  public static final long DEFAULT_CONNECT_TIMEOUT = 10000;

  private String host;
  private int port;
  private String username;
  private String password;
  private ProxyType type;
  private long connectTimeout;

  /**
   * Default constructor.
   */
  public ProxyOptions() {
    host = DEFAULT_HOST;
    port = DEFAULT_PORT;
    type = DEFAULT_TYPE;
    connectTimeout = DEFAULT_CONNECT_TIMEOUT;
  }

  /**
   * Copy constructor.
   *
   * @param other  the options to copy
   */
  public ProxyOptions(ProxyOptions other) {
    host = other.getHost();
    port = other.getPort();
    username = other.getUsername();
    password = other.getPassword();
    type = other.getType();
    connectTimeout = other.getConnectTimeout();
  }

  /**
   * Create options from JSON.
   *
   * @param json  the JSON
   */
  public ProxyOptions(JsonObject json) {
    this();
    ProxyOptionsConverter.fromJson(json, this);
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    ProxyOptionsConverter.toJson(this, json);
    return json;
  }

  /**
   * Get proxy host.
   *
   * @return  proxy hosts
   */
  public String getHost() {
    return host;
  }

  /**
   * Set proxy host.
   *
   * @param host the proxy host to connect to
   * @return a reference to this, so the API can be used fluently
   */
  public ProxyOptions setHost(String host) {
    Objects.requireNonNull(host, "Proxy host may not be null");
    this.host = host;
    return this;
  }

  /**
   * Get proxy port.
   *
   * @return  proxy port
   */
  public int getPort() {
    return port;
  }

  /**
   * Set proxy port.
   *
   * @param port the proxy port to connect to
   * @return a reference to this, so the API can be used fluently
   */
  public ProxyOptions setPort(int port) {
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("Invalid proxy port " + port);
    }
    this.port = port;
    return this;
  }

  /**
   * Get proxy username.
   *
   * @return  proxy username
   */
  public String getUsername() {
    return username;
  }

  /**
   * Set proxy username.
   *
   * @param username the proxy username
   * @return a reference to this, so the API can be used fluently
   */
  public ProxyOptions setUsername(String username) {
    this.username = username;
    return this;
  }

  /**
   * Get proxy password.
   *
   * @return  proxy password
   */
  public String getPassword() {
    return password;
  }

  /**
   * Set proxy password.
   *
   * @param password the proxy password
   * @return a reference to this, so the API can be used fluently
   */
  public ProxyOptions setPassword(String password) {
    this.password = password;
    return this;
  }

  /**
   * Get proxy type.
   *
   *<p>ProxyType can be HTTP, SOCKS4 and SOCKS5
   *
   * @return  proxy type
   */
  public ProxyType getType() {
    return type;
  }

  /**
   * Set proxy type.
   *
   * <p>ProxyType can be HTTP, SOCKS4 and SOCKS5
   *
   * @param type the proxy type to connect to
   * @return a reference to this, so the API can be used fluently
   */
  public ProxyOptions setType(ProxyType type) {
    Objects.requireNonNull(type, "Proxy type may not be null");
    this.type = type;
    return this;
  }

  /**
   * Get the connection timeout in milliseconds, defaults to {@code 10000}.
   * <p>
   * A connection to the proxy is considered successful when:
   *
   * <ul>
   *   <li>the client received a {@code 200} response to the {@code CONNECT} request for HTTP proxies, or</li>
   *   <li>the {@code SOCKS} handshake ended with the {@code SUCCESS} status, for SOCKS proxies.</li>
   * </ul>
   *
   * @return the connection timeout
   */
  public long getConnectTimeout() {
    return connectTimeout;
  }

  /**
   * Set the connection timeout in milliseconds.
   *
   * @param connectTimeout the connection timeout
   * @return a reference to this, so the API can be used fluently
   */
  public ProxyOptions setConnectTimeout(long connectTimeout) {
    this.connectTimeout = connectTimeout;
    return this;
  }
}
