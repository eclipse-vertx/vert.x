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

package io.vertx.core.dns;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;

/**
 * Configuration options for Vert.x DNS client.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true)
public class DnsClientOptions {

  /**
   * The default value for the port = {@code -1} (configured by {@link VertxOptions#getAddressResolverOptions()})
   */
  public static final int DEFAULT_PORT = -1;

  /**
   * The default value for the host = {@code null} (configured by {@link VertxOptions#getAddressResolverOptions()})
   */
  public static final String DEFAULT_HOST = null;

  /**
   * The default value for the query timeout in millis = {@code 5000}
   */
  public static final long DEFAULT_QUERY_TIMEOUT = 5000;

  /**
   * The default log enabled = false
   */
  public static final boolean DEFAULT_LOG_ENABLED = false;

  /**
  * The default value for the recursion desired flag (RD) = {@code true}
  */
  public static final boolean DEFAULT_RECURSION_DESIRED = true;
  
  private int port = DEFAULT_PORT;
  private String host = DEFAULT_HOST;
  private long queryTimeout = DEFAULT_QUERY_TIMEOUT;
  private boolean logActivity = DEFAULT_LOG_ENABLED;
  private boolean recursionDesired = DEFAULT_RECURSION_DESIRED;
  
  public DnsClientOptions() {
  }

  public DnsClientOptions(JsonObject json) {
    DnsClientOptionsConverter.fromJson(json, this);
  }

  public DnsClientOptions(DnsClientOptions other) {
    port = other.port;
    host = other.host;
    queryTimeout = other.queryTimeout;
    logActivity = other.logActivity;
    recursionDesired = other.recursionDesired;
  }

  /**
   * Get the port to be used by this client in requests.
   *
   * @return  the port
   */
  public int getPort() {
    return port;
  }

  /**
   * Set the port to be used by this client in requests.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public DnsClientOptions setPort(int port) {
    if (port<1 && port!=DEFAULT_PORT) {
      throw new IllegalArgumentException("DNS client port " + port + " must be > 0 or equal to DEFAULT_PORT");
    }
    this.port = port;
    return this;
  }

  /**
   * Get the host name to be used by this client in requests.
   *
   * @return  the host name
   */
  public String getHost() {
    return host;
  }

  /**
   * Set the host name to be used by this client in requests.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public DnsClientOptions setHost(String host) {
    this.host = host;
    return this;
  }

  /**
   * @return the query timeout in milliseconds
   */
  public long getQueryTimeout() {
    return queryTimeout;
  }

  /**
   * Set the query timeout in milliseconds, i.e the amount of time after a query is considered to be failed.
   *
   * @param queryTimeout the query timeout in milliseconds
   * @return a reference to this, so the API can be used fluently
   */
  public DnsClientOptions setQueryTimeout(long queryTimeout) {
    if (queryTimeout < 1) {
      throw new IllegalArgumentException("queryTimeout must be > 0");
    }
    this.queryTimeout = queryTimeout;
    return this;
  }

  /**
   * @return {@code true} when network activity logging is enabled
   */
  public boolean getLogActivity() {
    return logActivity;
  }

  /**
   * Set to true to enabled network activity logging: Netty's pipeline is configured for logging on Netty's logger.
   *
   * @param logActivity true for logging the network activity
   * @return a reference to this, so the API can be used fluently
   */
  public DnsClientOptions setLogActivity(boolean logActivity) {
    this.logActivity = logActivity;
    return this;
  }

  /**
   * Return whether or not recursion is desired
   *
   * @return {@code true} when recursion is desired
   */
  public boolean isRecursionDesired() {
    return recursionDesired;
  }
  
  /**
   * Set whether or not recursion is desired
   *
   * @param recursionDesired the new value
   * @return a reference to this, so the API can be used fluently
   */
  public DnsClientOptions setRecursionDesired(boolean recursionDesired) {
    this.recursionDesired = recursionDesired;
    return this;
  }
  
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    DnsClientOptionsConverter.toJson(this, json);
    return json;
  }
}
