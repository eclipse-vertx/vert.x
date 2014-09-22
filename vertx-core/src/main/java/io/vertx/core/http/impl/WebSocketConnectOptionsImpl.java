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

import io.vertx.core.Headers;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebSocketConnectOptionsImpl implements WebSocketConnectOptions {

  private static final int DEFAULT_PORT = 80;
  private static final String DEFAULT_HOST = "localhost";
  private static final String DEFAULT_REQUEST_URI = "/";
  private static final int DEFAULT_MAXWEBSOCKETFRAMESIZE = 65536;
  private static final int DEFAULT_WEBSOCKETVERSION = 13;

  private int port ;
  private String host;
  private Headers headers;
  private String requestURI;
  private int maxWebsocketFrameSize;
  private int version;
  private Set<String> subProtocols = new HashSet<>();

  WebSocketConnectOptionsImpl() {
    this.port = DEFAULT_PORT;
    this.host = DEFAULT_HOST;
    this.requestURI = DEFAULT_REQUEST_URI;
    this.maxWebsocketFrameSize = DEFAULT_MAXWEBSOCKETFRAMESIZE;
    this.version = DEFAULT_WEBSOCKETVERSION;
  }

  WebSocketConnectOptionsImpl(WebSocketConnectOptions other) {
    this.port = other.getPort();
    this.host = other.getHost();
    this.requestURI = other.getRequestURI();
    this.headers = other.getHeaders();
    this.maxWebsocketFrameSize = other.getMaxWebsocketFrameSize();
    this.version = other.getVersion();
    this.subProtocols = other.getSubProtocols() != null ? new HashSet<>(other.getSubProtocols()) : null;
  }

  WebSocketConnectOptionsImpl(JsonObject json) {
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
    this.maxWebsocketFrameSize = json.getInteger("maxWebsocketFrameSize", DEFAULT_MAXWEBSOCKETFRAMESIZE);
    this.version = json.getInteger("version", DEFAULT_WEBSOCKETVERSION);
    JsonArray arr = json.getArray("subProtocols");
    this.subProtocols = new HashSet<>();
    if (arr != null) {
      subProtocols.addAll(arr.toList());
    }
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public WebSocketConnectOptions setPort(int port) {
    Arguments.requireInRange(port, 1, 65535, "port p must be in range 1 <= p <= 65535");
    this.port = port;
    return this;
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public WebSocketConnectOptions setHost(String host) {
    Objects.requireNonNull(host);
    this.host = host;
    return this;
  }

  @Override
  public Headers getHeaders() {
    return headers;
  }

  @Override
  public WebSocketConnectOptions setHeaders(Headers headers) {
    this.headers = headers;
    return this;
  }

  @Override
  public String getRequestURI() {
    return requestURI;
  }

  @Override
  public WebSocketConnectOptions setRequestURI(String requestURI) {
    this.requestURI = requestURI;
    return this;
  }

  @Override
  public WebSocketConnectOptions addHeader(CharSequence name, CharSequence value) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
    if (headers == null) {
      headers = new CaseInsensitiveHeaders();
    }
    headers.add(name, value);
    return this;
  }

  public int getMaxWebsocketFrameSize() {
    return maxWebsocketFrameSize;
  }

  public WebSocketConnectOptions setMaxWebsocketFrameSize(int maxWebsocketFrameSize) {
    Arguments.require(maxWebsocketFrameSize > 0, "maxWebsocketFrameSize must be > 0");
    this.maxWebsocketFrameSize = maxWebsocketFrameSize;
    return this;
  }

  public int getVersion() {
    return version;
  }

  public WebSocketConnectOptions setVersion(int version) {
    Objects.requireNonNull(version);
    Arguments.require(0 == version || 8 == version || 13 == version, "version must be 0 or 8 or 13");
    this.version = version;
    return this;
  }

  public WebSocketConnectOptions addSubProtocol(String subProtocol) {
    subProtocols.add(subProtocol);
    return this;
  }

  public Set<String> getSubProtocols() {
    return subProtocols;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    WebSocketConnectOptionsImpl that = (WebSocketConnectOptionsImpl) o;

    if (maxWebsocketFrameSize != that.maxWebsocketFrameSize) return false;
    if (port != that.port) return false;
    if (version != that.version) return false;
    if (headers != null ? !headers.equals(that.headers) : that.headers != null) return false;
    if (host != null ? !host.equals(that.host) : that.host != null) return false;
    if (requestURI != null ? !requestURI.equals(that.requestURI) : that.requestURI != null) return false;
    if (subProtocols != null ? !subProtocols.equals(that.subProtocols) : that.subProtocols != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = port;
    result = 31 * result + (host != null ? host.hashCode() : 0);
    result = 31 * result + (headers != null ? headers.hashCode() : 0);
    result = 31 * result + (requestURI != null ? requestURI.hashCode() : 0);
    result = 31 * result + maxWebsocketFrameSize;
    result = 31 * result + version;
    result = 31 * result + (subProtocols != null ? subProtocols.hashCode() : 0);
    return result;
  }
}
