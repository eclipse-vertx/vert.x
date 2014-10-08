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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class WebsocketConnectOptions {

  public static final WebsocketVersion DEFAULT_WEBSOCKET_VERSION = WebsocketVersion.V13;

  private WebsocketVersion version;
  private Set<String> subProtocols = new HashSet<>();

  public WebsocketConnectOptions() {
    this.version = DEFAULT_WEBSOCKET_VERSION;
  }

  public WebsocketConnectOptions(WebsocketConnectOptions other) {
    this.version = other.getVersion();
    this.subProtocols = other.getSubProtocols() != null ? new HashSet<>(other.getSubProtocols()) : null;
  }

  public WebsocketConnectOptions(JsonObject json) {
    this.version = WebsocketVersion.valueOf(json.getString("version", DEFAULT_WEBSOCKET_VERSION.toString()));
    JsonArray arr = json.getArray("subProtocols");
    this.subProtocols = new HashSet<>();
    if (arr != null) {
      subProtocols.addAll(arr.toList());
    }
  }

  public WebsocketVersion getVersion() {
    return version;
  }

  public WebsocketConnectOptions setVersion(WebsocketVersion version) {
    Objects.requireNonNull(version);
    this.version = version;
    return this;
  }

  public WebsocketConnectOptions addSubProtocol(String subProtocol) {
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

    WebsocketConnectOptions that = (WebsocketConnectOptions) o;

    if (version != that.version) return false;
    if (subProtocols != null ? !subProtocols.equals(that.subProtocols) : that.subProtocols != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = version.hashCode();
    result = 31 * result + (subProtocols != null ? subProtocols.hashCode() : 0);
    return result;
  }
}
