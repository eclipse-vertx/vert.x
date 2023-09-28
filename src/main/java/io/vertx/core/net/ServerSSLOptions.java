/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Server SSL options.
 */
@DataObject(generateConverter = true, publicConverter = false)
public class ServerSSLOptions extends SSLOptions {

  /**
   * Default value of whether client auth is required (SSL/TLS) = No
   */
  public static final ClientAuth DEFAULT_CLIENT_AUTH = ClientAuth.NONE;

  /**
   * Default value of whether the server supports SNI = false
   */
  public static final boolean DEFAULT_SNI = false;

  private ClientAuth clientAuth;
  private boolean sni;

  /**
   * Default constructor
   */
  public ServerSSLOptions() {
    super();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public ServerSSLOptions(ServerSSLOptions other) {
    super(other);
    clientAuth = other.clientAuth;
    sni = other.sni;
  }

  /**
   * Create options from JSON
   *
   * @param json the JSON
   */
  public ServerSSLOptions(JsonObject json) {
    super(json);
    ServerSSLOptionsConverter.fromJson(json, this);
  }

  @Override
  protected void init() {
    super.init();
    this.clientAuth = DEFAULT_CLIENT_AUTH;
    this.sni = DEFAULT_SNI;
  }

  public ServerSSLOptions copy() {
    return new ServerSSLOptions(this);
  }

  public ClientAuth getClientAuth() {
    return clientAuth;
  }

  /**
   * Set whether client auth is required
   *
   * @param clientAuth One of "NONE, REQUEST, REQUIRED". If it's set to "REQUIRED" then server will require the
   *                   SSL cert to be presented otherwise it won't accept the request. If it's set to "REQUEST" then
   *                   it won't mandate the certificate to be presented, basically make it optional.
   * @return a reference to this, so the API can be used fluently
   */
  public ServerSSLOptions setClientAuth(ClientAuth clientAuth) {
    this.clientAuth = clientAuth;
    return this;
  }

  /**
   * @return whether the server supports Server Name Indication
   */
  public boolean isSni() {
    return sni;
  }

  /**
   * Set whether the server supports Server Name Indiciation
   *
   * @return a reference to this, so the API can be used fluently
   */
  public ServerSSLOptions setSni(boolean sni) {
    this.sni = sni;
    return this;
  }

  @Override
  public ServerSSLOptions setKeyCertOptions(KeyCertOptions options) {
    return (ServerSSLOptions) super.setKeyCertOptions(options);
  }

  @Override
  public ServerSSLOptions setTrustOptions(TrustOptions options) {
    return (ServerSSLOptions) super.setTrustOptions(options);
  }

  @Override
  public ServerSSLOptions setUseAlpn(boolean useAlpn) {
    return (ServerSSLOptions) super.setUseAlpn(useAlpn);
  }

  @Override
  public ServerSSLOptions setSslHandshakeTimeout(long sslHandshakeTimeout) {
    return (ServerSSLOptions) super.setSslHandshakeTimeout(sslHandshakeTimeout);
  }

  @Override
  public ServerSSLOptions setSslHandshakeTimeoutUnit(TimeUnit sslHandshakeTimeoutUnit) {
    return (ServerSSLOptions) super.setSslHandshakeTimeoutUnit(sslHandshakeTimeoutUnit);
  }

  @Override
  public ServerSSLOptions setEnabledSecureTransportProtocols(Set<String> enabledSecureTransportProtocols) {
    return (ServerSSLOptions) super.setEnabledSecureTransportProtocols(enabledSecureTransportProtocols);
  }

  @Override
  public ServerSSLOptions setApplicationLayerProtocols(List<String> protocols) {
    return (ServerSSLOptions) super.setApplicationLayerProtocols(protocols);
  }

  @Override
  public ServerSSLOptions addEnabledCipherSuite(String suite) {
    return (ServerSSLOptions) super.addEnabledCipherSuite(suite);
  }

  @Override
  public ServerSSLOptions addCrlPath(String crlPath) throws NullPointerException {
    return (ServerSSLOptions) super.addCrlPath(crlPath);
  }

  @Override
  public ServerSSLOptions addCrlValue(Buffer crlValue) throws NullPointerException {
    return (ServerSSLOptions) super.addCrlValue(crlValue);
  }

  @Override
  public ServerSSLOptions addEnabledSecureTransportProtocol(String protocol) {
    return (ServerSSLOptions) super.addEnabledSecureTransportProtocol(protocol);
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = super.toJson();
    ServerSSLOptionsConverter.toJson(this, json);
    return json;
  }
}
