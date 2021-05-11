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
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.ProxyOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Options describing how an {@link HttpClient} connect a {@link WebSocket}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true)
public class WebSocketConnectOptions extends RequestOptions {

  /**
   * The default value for proxy options = {@code null}
   */
  public static final ProxyOptions DEFAULT_PROXY_OPTIONS = null;

  /**
   * The default WebSocket version = {@link WebsocketVersion#V13}
   */
  public static final WebsocketVersion DEFAULT_VERSION = WebsocketVersion.V13;

  /**
   * The default WebSocket sub protocols = {@code null}
   */
  public static final List<String> DEFAULT_SUB_PROTOCOLS = null;

  private ProxyOptions proxyOptions;
  private WebsocketVersion version;
  private List<String> subProtocols;

  public WebSocketConnectOptions() {
    proxyOptions = DEFAULT_PROXY_OPTIONS;
    version = DEFAULT_VERSION;
    subProtocols = DEFAULT_SUB_PROTOCOLS;
  }

  public WebSocketConnectOptions(WebSocketConnectOptions other) {
    super(other);
    this.proxyOptions = other.proxyOptions != null ? new ProxyOptions(other.proxyOptions) : null;
    this.version = other.version;
    this.subProtocols = other.subProtocols;
  }

  public WebSocketConnectOptions(JsonObject json) {
    super(json);
    WebSocketConnectOptionsConverter.fromJson(json, this);
  }

  /**
   * @return the WebSocket version
   */
  public WebsocketVersion getVersion() {
    return version;
  }

  /**
   * Set the WebSocket version.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketConnectOptions setVersion(WebsocketVersion version) {
    this.version = version;
    return this;
  }

  /**
   * @return the list of WebSocket sub protocols or {@code null} if there are none
   */
  public List<String> getSubProtocols() {
    return subProtocols;
  }

  /**
   * Set the WebSocket sub protocols to use.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketConnectOptions setSubProtocols(List<String> subProtocols) {
    this.subProtocols = subProtocols;
    return this;
  }

  /**
   * Add a WebSocket sub protocol to use.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketConnectOptions addSubProtocol(String subprotocol) {
    if (subProtocols == null) {
      subProtocols = new ArrayList<>();
    }
    subProtocols.add(subprotocol);
    return this;
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
  public RequestOptions setProxyOptions(ProxyOptions proxyOptions) {
    this.proxyOptions = proxyOptions;
    return this;
  }

  @Override
  public WebSocketConnectOptions setHost(String host) {
    return (WebSocketConnectOptions) super.setHost(host);
  }

  @Override
  public WebSocketConnectOptions setPort(Integer port) {
    return (WebSocketConnectOptions) super.setPort(port);
  }

  @Override
  public WebSocketConnectOptions setSsl(Boolean ssl) {
    return (WebSocketConnectOptions) super.setSsl(ssl);
  }

  @Override
  public WebSocketConnectOptions setURI(String uri) {
    return (WebSocketConnectOptions) super.setURI(uri);
  }

  @Override
  public WebSocketConnectOptions addHeader(String key, String value) {
    return (WebSocketConnectOptions) super.addHeader(key, value);
  }

  @GenIgnore
  @Override
  public WebSocketConnectOptions setHeaders(MultiMap headers) {
    return (WebSocketConnectOptions) super.setHeaders(headers);
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = super.toJson();
    WebSocketConnectOptionsConverter.toJson(this, json);
    return json;
  }
}
