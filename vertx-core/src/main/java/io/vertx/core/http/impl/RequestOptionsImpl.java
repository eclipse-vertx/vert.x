/*
 * Copyright 2014 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
 */

package io.vertx.core.http.impl;

import io.vertx.core.MultiMap;
import io.vertx.core.http.CaseInsensitiveMultiMap;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.http.RequestOptionsBase;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RequestOptionsImpl implements RequestOptions {

  private static final int DEFAULT_PORT = 80;
  private static final String DEFAULT_HOST = "localhost";
  private static final String DEFAULT_REQUEST_URI = "/";

  private int port ;
  private String host;
  private MultiMap headers;
  private String requestURI;

  RequestOptionsImpl() {
    this.port = DEFAULT_PORT;
    this.host = DEFAULT_HOST;
    this.requestURI = DEFAULT_REQUEST_URI;
  }

  RequestOptionsImpl(RequestOptionsBase other) {
    this.port = other.getPort();
    this.host = other.getHost();
    this.requestURI = other.getRequestURI();
    this.headers = other.getHeaders();
  }

  RequestOptionsImpl(JsonObject json) {
    this.port = json.getInteger("port", DEFAULT_PORT);
    this.host = json.getString("host", DEFAULT_HOST);
    this.requestURI = json.getString("requestURI", DEFAULT_REQUEST_URI);
    JsonObject obj = json.getObject("headers");
    if (obj == null) {
      headers = null;
    } else {
      headers = new CaseInsensitiveMultiMap();
      obj.toMap().forEach((k, v) -> {
        headers.set(k, (String)v);
      });
    }
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public RequestOptions setPort(int port) {
    if (port < 1|| port > 65535) {
      throw new IllegalArgumentException("port p must be in range 1 <=p <= 65535");
    }
    this.port = port;
    return this;
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public RequestOptions setHost(String host) {
    this.host = host;
    return this;
  }

  @Override
  public MultiMap getHeaders() {
    return headers;
  }

  @Override
  public RequestOptions setHeaders(MultiMap headers) {
    this.headers = headers;
    return this;
  }

  @Override
  public String getRequestURI() {
    return requestURI;
  }

  @Override
  public RequestOptions setRequestURI(String requestURI) {
    this.requestURI = requestURI;
    return this;
  }

  @Override
  public RequestOptions putHeader(CharSequence name, CharSequence value) {
    if (name == null) {
      throw new NullPointerException("name");
    }
    if (value == null) {
      throw new NullPointerException("value");
    }
    if (headers == null) {
      headers = new CaseInsensitiveMultiMap();
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
