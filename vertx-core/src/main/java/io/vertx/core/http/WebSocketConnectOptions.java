/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.core.http;

import io.vertx.core.MultiMap;
import io.vertx.codegen.annotations.Options;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class WebSocketConnectOptions extends RequestOptions {

  private static final int DEFAULT_MAXWEBSOCKETFRAMESIZE = 65536;
  private static final int DEFAULT_WEBSOCKETVERSION = 13;

  private int maxWebsocketFrameSize;
  private int version;
  private Set<String> subProtocols = new HashSet<>();

  public WebSocketConnectOptions() {
    this.maxWebsocketFrameSize = DEFAULT_MAXWEBSOCKETFRAMESIZE;
    this.version = DEFAULT_WEBSOCKETVERSION;
  }

  public WebSocketConnectOptions(WebSocketConnectOptions other) {
    this.maxWebsocketFrameSize = other.maxWebsocketFrameSize;
    this.version = other.version;
    this.subProtocols = other.subProtocols != null ? new HashSet<>(other.subProtocols) : null;
  }

  public WebSocketConnectOptions(JsonObject json) {
    this.maxWebsocketFrameSize = json.getInteger("maxWebsocketFrameSize", DEFAULT_MAXWEBSOCKETFRAMESIZE);
    this.version = json.getInteger("version", DEFAULT_WEBSOCKETVERSION);
    JsonArray arr = json.getArray("subProtocols");
    this.subProtocols = arr == null ? null : new HashSet<String>(arr.toList());
  }

  public int getMaxWebsocketFrameSize() {
    return maxWebsocketFrameSize;
  }

  public WebSocketConnectOptions setMaxWebsocketFrameSize(int maxWebsocketFrameSize) {
    if (maxWebsocketFrameSize < 1) {
      throw new IllegalArgumentException("maxWebsocketFrameSize must be > 0");
    }
    this.maxWebsocketFrameSize = maxWebsocketFrameSize;
    return this;
  }

  public int getVersion() {
    return version;
  }

  public WebSocketConnectOptions setVersion(int version) {
    Objects.requireNonNull(version);
    if (version != 0 && version != 8 && version != 13 ) {
      throw new IllegalArgumentException("version must be 0 or 8 or 13");
    }
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
  public WebSocketConnectOptions setPort(int port) {
    super.setPort(port);
    return this;
  }

  @Override
  public WebSocketConnectOptions setHost(String host) {
    super.setHost(host);
    return this;
  }

  @Override
  public WebSocketConnectOptions setHeaders(MultiMap headers) {
    super.setHeaders(headers);
    return this;
  }

  @Override
  public WebSocketConnectOptions setRequestURI(String requestURI) {
    super.setRequestURI(requestURI);
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WebSocketConnectOptions)) return false;
    if (!super.equals(o)) return false;

    WebSocketConnectOptions that = (WebSocketConnectOptions) o;

    if (maxWebsocketFrameSize != that.maxWebsocketFrameSize) return false;
    if (version != that.version) return false;
    if (subProtocols != null ? !subProtocols.equals(that.subProtocols) : that.subProtocols != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + maxWebsocketFrameSize;
    result = 31 * result + version;
    result = 31 * result + (subProtocols != null ? subProtocols.hashCode() : 0);
    return result;
  }
}
