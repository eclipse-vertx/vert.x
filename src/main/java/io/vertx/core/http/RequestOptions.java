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

package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;

import java.util.Objects;

/**
 * Options describing how an {@link HttpClient} will make connect to make a request.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true)
public class RequestOptions {

  /**
   * The default value for host name = "localhost"
   */
  public static final String DEFAULT_HOST = "localhost";

  /**
   * The default value for port = 80
   */
  public static final int DEFAULT_PORT = 80;

  /**
   * SSL enabled by default = false
   */
  public static final Boolean DEFAULT_SSL = null;

  /**
   * The default relative request URI = ""
   */
  public static final String DEFAULT_URI = "";

  private String host;
  private int port;
  private Boolean ssl;
  private String uri;
  private MultiMap headers;

  /**
   * Default constructor
   */
  public RequestOptions() {
    host = DEFAULT_HOST;
    port = DEFAULT_PORT;
    ssl = DEFAULT_SSL;
    uri = DEFAULT_URI;
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public RequestOptions(RequestOptions other) {
    setHost(other.host);
    setPort(other.port);
    setSsl(other.ssl);
    setURI(other.uri);

  }

  /**
   * Create options from JSON
   *
   * @param json the JSON
   */
  public RequestOptions(JsonObject json) {
    RequestOptionsConverter.fromJson(json, this);
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
  public RequestOptions setHost(String host) {
    this.host = host;
    return this;
  }

  /**
   * Get the port to be used by the client request.
   *
   * @return  the port
   */
  public int getPort() {
    return port;
  }

  /**
   * Set the port to be used by the client request.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public RequestOptions setPort(int port) {
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
   * Set whether SSL/TLS is enabled
   *
   * @param ssl  true if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public RequestOptions setSsl(Boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  /**
   * @return the request relative URI
   */
  public String getURI() {
    return uri;
  }

  /**
   * Set the request relative URI
   *
   * @param uri  the relative uri
   * @return a reference to this, so the API can be used fluently
   */
  public RequestOptions setURI(String uri) {
    this.uri = uri;
    return this;
  }

  /**
   * Add a request header.
   *
   * @param key  the header key
   * @param value  the header value
   * @return a reference to this, so the API can be used fluently
   */
  public RequestOptions addHeader(String key, String value) {
    checkHeaders();
    Objects.requireNonNull(key, "no null key accepted");
    Objects.requireNonNull(value, "no null value accepted");
    headers.add(key, value);
    return this;
  }

  /**
   * Set request headers from a multi-map.
   *
   * @param headers  the headers
   * @return  a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public RequestOptions setHeaders(MultiMap headers) {
    this.headers = headers;
    return this;
  }

  /**
   * Get the request headers
   *
   * @return  the headers
   */
  @GenIgnore
  public MultiMap getHeaders() {
    return headers;
  }

  private void checkHeaders() {
    if (headers == null) {
      headers = new CaseInsensitiveHeaders();
    }
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    RequestOptionsConverter.toJson(this, json);
    return json;
  }
}
