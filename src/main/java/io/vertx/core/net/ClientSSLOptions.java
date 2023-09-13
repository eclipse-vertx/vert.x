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
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Client SSL options.
 */
@DataObject(generateConverter = true, publicConverter = false)
public class ClientSSLOptions extends SSLOptions {

  /**
   * Default value to determine hostname verification algorithm hostname verification (for SSL/TLS) = ""
   */
  public static final String DEFAULT_HOSTNAME_VERIFICATION_ALGORITHM = "";

  /**
   * The default value of whether all servers (SSL/TLS) should be trusted = false
   */
  public static final boolean DEFAULT_TRUST_ALL = false;

  private String hostnameVerificationAlgorithm;
  private boolean trustAll;

  /**
   * Default constructor
   */
  public ClientSSLOptions() {
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public ClientSSLOptions(ClientSSLOptions other) {
    super(other);

    hostnameVerificationAlgorithm = other.getHostnameVerificationAlgorithm();
    trustAll = other.isTrustAll();
  }

  /**
   * Create options from JSON
   *
   * @param json the JSON
   */
  public ClientSSLOptions(JsonObject json) {
    super(json);
    ClientSSLOptionsConverter.fromJson(json, this);
  }

  @Override
  protected void init() {
    super.init();
    hostnameVerificationAlgorithm = DEFAULT_HOSTNAME_VERIFICATION_ALGORITHM;
    trustAll = DEFAULT_TRUST_ALL;
  }

  @Override
  public ClientSSLOptions copy() {
    return new ClientSSLOptions(this);
  }

  /**
   * @return the value of the hostname verification algorithm
   */
  public String getHostnameVerificationAlgorithm() {
    return hostnameVerificationAlgorithm;
  }

  /**
   * Set the hostname verification algorithm interval
   *
   * @param hostnameVerificationAlgorithm should be HTTPS, LDAPS or a {@code null}
   * @return a reference to this, so the API can be used fluently
   */
  public ClientSSLOptions setHostnameVerificationAlgorithm(String hostnameVerificationAlgorithm) {
    this.hostnameVerificationAlgorithm = hostnameVerificationAlgorithm;
    return this;
  }

  /**
   * @return {@code true} if all server certificates should be trusted
   */
  public boolean isTrustAll() {
    return trustAll;
  }

  /**
   * Set whether all server certificates should be trusted
   *
   * @param trustAll {@code true} if all should be trusted
   * @return a reference to this, so the API can be used fluently
   */
  public ClientSSLOptions setTrustAll(boolean trustAll) {
    this.trustAll = trustAll;
    return this;
  }

  @Override
  public ClientSSLOptions setKeyCertOptions(KeyCertOptions options) {
    return (ClientSSLOptions) super.setKeyCertOptions(options);
  }

  @Override
  public ClientSSLOptions setTrustOptions(TrustOptions options) {
    return (ClientSSLOptions) super.setTrustOptions(options);
  }

  @Override
  public ClientSSLOptions setUseAlpn(boolean useAlpn) {
    return (ClientSSLOptions) super.setUseAlpn(useAlpn);
  }

  @Override
  public ClientSSLOptions setSslHandshakeTimeout(long sslHandshakeTimeout) {
    return (ClientSSLOptions) super.setSslHandshakeTimeout(sslHandshakeTimeout);
  }

  @Override
  public ClientSSLOptions setSslHandshakeTimeoutUnit(TimeUnit sslHandshakeTimeoutUnit) {
    return (ClientSSLOptions) super.setSslHandshakeTimeoutUnit(sslHandshakeTimeoutUnit);
  }

  @Override
  public ClientSSLOptions setEnabledSecureTransportProtocols(Set<String> enabledSecureTransportProtocols) {
    return (ClientSSLOptions) super.setEnabledSecureTransportProtocols(enabledSecureTransportProtocols);
  }

  @Override
  public ClientSSLOptions setApplicationLayerProtocols(List<String> protocols) {
    return (ClientSSLOptions) super.setApplicationLayerProtocols(protocols);
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = super.toJson();
    ClientSSLOptionsConverter.toJson(this, json);
    return json;
  }
}
