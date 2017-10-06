/*
 * Copyright (c) 2011-2013 The original author or authors
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
package io.vertx.core.dns;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * Configuration options for Vert.x DNS client.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true)
public class DnsClientOptions {

  /**
   * The default value for the port = 53
   */
  public static final int DEFAULT_PORT = 53;

  /**
   * The default value for the host = localhost
   */
  public static final String DEFAULT_HOST = "localhost";

  /**
   * The default value for the query timeout in millis = 5000
   */
  public static final long DEFAULT_QUERY_TIMEOUT = 5000;

  private int port = DEFAULT_PORT;
  private String host = DEFAULT_HOST;
  private long queryTimeout = DEFAULT_QUERY_TIMEOUT;

  public DnsClientOptions() {
  }

  public DnsClientOptions(JsonObject json) {
    DnsClientOptionsConverter.fromJson(json, this);
  }

  public DnsClientOptions(DnsClientOptions other) {
    port = other.port;
    host = other.host;
    queryTimeout = other.queryTimeout;
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

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    DnsClientOptionsConverter.toJson(this, json);
    return json;
  }
}
