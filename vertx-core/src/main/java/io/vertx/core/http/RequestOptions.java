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

package io.vertx.core.http;

import io.vertx.codegen.annotations.Options;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;

import java.util.Objects;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class RequestOptions {

  private static final int DEFAULT_PORT = 80;
  private static final String DEFAULT_HOST = "localhost";
  private static final String DEFAULT_REQUEST_URI = "/";

  private int port ;
  private String host;
  private MultiMap headers;
  private String requestURI;

  public RequestOptions() {
    this.port = DEFAULT_PORT;
    this.host = DEFAULT_HOST;
    this.requestURI = DEFAULT_REQUEST_URI;
  }

  public RequestOptions(RequestOptions other) {
    this.port = other.getPort();
    this.host = other.getHost();
    this.requestURI = other.getRequestURI();
    this.headers = other.getHeaders();
  }

  public RequestOptions(JsonObject json) {
    this.port = json.getInteger("port", DEFAULT_PORT);
    this.host = json.getString("host", DEFAULT_HOST);
    this.requestURI = json.getString("requestURI", DEFAULT_REQUEST_URI);
    JsonObject obj = json.getObject("headers");
    if (obj == null) {
      headers = null;
    } else {
      headers = new CaseInsensitiveHeaders();
      obj.toMap().forEach((k, v) -> {
        headers.set(k, (String)v);
      });
    }
  }

  public int getPort() {
    return port;
  }

  public RequestOptions setPort(int port) {
    if (port < 1|| port > 65535) {
      throw new IllegalArgumentException("port p must be in range 1 <=p <= 65535");
    }
    this.port = port;
    return this;
  }

  public String getHost() {
    return host;
  }

  public RequestOptions setHost(String host) {
    this.host = host;
    return this;
  }

  public MultiMap getHeaders() {
    return headers;
  }

  public RequestOptions setHeaders(MultiMap headers) {
    this.headers = headers;
    return this;
  }

  public String getRequestURI() {
    return requestURI;
  }

  public RequestOptions setRequestURI(String requestURI) {
    this.requestURI = requestURI;
    return this;
  }

  public RequestOptions addHeader(CharSequence name, CharSequence value) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
    if (headers == null) {
      headers = new CaseInsensitiveHeaders();
    }
    headers.add(name, value);
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RequestOptions)) return false;

    RequestOptions that = (RequestOptions) o;

    if (port != that.getPort()) return false;
    if (headers != null ? !headers.equals(that.getHeaders()) : that.getHeaders() != null) return false;
    if (host != null ? !host.equals(that.getHost()) : that.getHost() != null) return false;
    if (requestURI != null ? !requestURI.equals(that.getRequestURI()) : that.getRequestURI() != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = port;
    result = 31 * result + (host != null ? host.hashCode() : 0);
    result = 31 * result + (headers != null ? headers.hashCode() : 0);
    result = 31 * result + (requestURI != null ? requestURI.hashCode() : 0);
    return result;
  }
}
