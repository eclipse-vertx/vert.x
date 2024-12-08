/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
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
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Client/Server SSL options.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class SSLOptions {

  /**
   * Default use alpn = false
   */
  public static final boolean DEFAULT_USE_ALPN = false;

  /**
   * Default use http3 = false
   */
  public static final boolean DEFAULT_HTTP3 = false;

  /**
   * Default use initialMaxStreamsBidirectional = 100
   */
  public static final long DEFAULT_INITIAL_MAX_STREAMS_BIDIRECTIONAL = 100;

  /**
   * The default value of SSL handshake timeout = 10
   */
  public static final long DEFAULT_SSL_HANDSHAKE_TIMEOUT = 10L;

  /**
   * Default SSL handshake time unit = SECONDS
   */
  public static final TimeUnit DEFAULT_SSL_HANDSHAKE_TIMEOUT_TIME_UNIT = TimeUnit.SECONDS;

  /**
   * The default ENABLED_SECURE_TRANSPORT_PROTOCOLS value = { "TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3" }
   * <p/>
   * SSLv3 is NOT enabled due to POODLE vulnerability http://en.wikipedia.org/wiki/POODLE
   * <p/>
   * "SSLv2Hello" is NOT enabled since it's disabled by default since JDK7
   */
  public static final List<String> DEFAULT_ENABLED_SECURE_TRANSPORT_PROTOCOLS = Collections.unmodifiableList(Arrays.asList("TLSv1.2", "TLSv1.3"));

  private long sslHandshakeTimeout;
  private TimeUnit sslHandshakeTimeoutUnit;
  private KeyCertOptions keyCertOptions;
  private TrustOptions trustOptions;
  Set<String> enabledCipherSuites;
  List<String> crlPaths;
  List<Buffer> crlValues;
  private boolean useAlpn;
  private boolean http3;
  private Set<String> enabledSecureTransportProtocols;
  private List<String> applicationLayerProtocols;
  private long initialMaxStreamsBidirectional;

  /**
   * Default constructor
   */
  public SSLOptions() {
    init();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public SSLOptions(SSLOptions other) {
    this.sslHandshakeTimeout = other.sslHandshakeTimeout;
    this.sslHandshakeTimeoutUnit = other.getSslHandshakeTimeoutUnit() != null ? other.getSslHandshakeTimeoutUnit() : DEFAULT_SSL_HANDSHAKE_TIMEOUT_TIME_UNIT;
    this.keyCertOptions = other.getKeyCertOptions() != null ? other.getKeyCertOptions().copy() : null;
    this.trustOptions = other.getTrustOptions() != null ? other.getTrustOptions().copy() : null;
    this.enabledCipherSuites = other.getEnabledCipherSuites() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(other.getEnabledCipherSuites());
    this.crlPaths = new ArrayList<>(other.getCrlPaths());
    this.crlValues = new ArrayList<>(other.getCrlValues());
    this.useAlpn = other.useAlpn;
    this.http3 = other.http3;
    this.initialMaxStreamsBidirectional = other.initialMaxStreamsBidirectional;
    this.enabledSecureTransportProtocols = other.getEnabledSecureTransportProtocols() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(other.getEnabledSecureTransportProtocols());
    this.applicationLayerProtocols = other.getApplicationLayerProtocols() != null ? new ArrayList<>(other.getApplicationLayerProtocols()) : null;
  }

  /**
   * Create options from JSON
   *
   * @param json the JSON
   */
  public SSLOptions(JsonObject json) {
    this();
    SSLOptionsConverter.fromJson(json ,this);
  }


  protected void init() {
    sslHandshakeTimeout = DEFAULT_SSL_HANDSHAKE_TIMEOUT;
    sslHandshakeTimeoutUnit = DEFAULT_SSL_HANDSHAKE_TIMEOUT_TIME_UNIT;
    enabledCipherSuites = new LinkedHashSet<>();
    crlPaths = new ArrayList<>();
    crlValues = new ArrayList<>();
    useAlpn = DEFAULT_USE_ALPN;
    http3 = DEFAULT_HTTP3;
    initialMaxStreamsBidirectional = DEFAULT_INITIAL_MAX_STREAMS_BIDIRECTIONAL;
    enabledSecureTransportProtocols = new LinkedHashSet<>(DEFAULT_ENABLED_SECURE_TRANSPORT_PROTOCOLS);
    applicationLayerProtocols = null;
  }

  public SSLOptions copy() {
    return new SSLOptions(this);
  }

  /**
   * @return the key/cert options
   */
  @GenIgnore
  public KeyCertOptions getKeyCertOptions() {
    return keyCertOptions;
  }

  /**
   * Set the key/cert options.
   *
   * @param options the key store options
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public SSLOptions setKeyCertOptions(KeyCertOptions options) {
    this.keyCertOptions = options;
    return this;
  }

  /**
   * @return the trust options
   */
  public TrustOptions getTrustOptions() {
    return trustOptions;
  }

  /**
   * Set the trust options.
   * @param options the trust options
   * @return a reference to this, so the API can be used fluently
   */
  public SSLOptions setTrustOptions(TrustOptions options) {
    this.trustOptions = options;
    return this;
  }

  /**
   * Add an enabled cipher suite, appended to the ordered suites.
   *
   * @param suite  the suite
   * @return a reference to this, so the API can be used fluently
   * @see #getEnabledCipherSuites()
   */
  public SSLOptions addEnabledCipherSuite(String suite) {
    enabledCipherSuites.add(suite);
    return this;
  }

  /**
   * Removes an enabled cipher suite from the ordered suites.
   *
   * @param suite  the suite
   * @return a reference to this, so the API can be used fluently
   */
  public SSLOptions removeEnabledCipherSuite(String suite) {
    enabledCipherSuites.remove(suite);
    return this;
  }

  /**
   * Return an ordered set of the cipher suites.
   *
   * <p> The set is initially empty and suite should be added to this set in the desired order.
   *
   * <p> When suites are added and therefore the list is not empty, it takes precedence over the
   * default suite defined by the {@link SSLEngineOptions} in use.
   *
   * @return the enabled cipher suites
   */
  public Set<String> getEnabledCipherSuites() {
    return enabledCipherSuites;
  }

  /**
   *
   * @return the CRL (Certificate revocation list) paths
   */
  public List<String> getCrlPaths() {
    return crlPaths;
  }

  /**
   * Add a CRL path
   * @param crlPath  the path
   * @return a reference to this, so the API can be used fluently
   * @throws NullPointerException
   */
  public SSLOptions addCrlPath(String crlPath) throws NullPointerException {
    Objects.requireNonNull(crlPath, "No null crl accepted");
    crlPaths.add(crlPath);
    return this;
  }

  /**
   * Get the CRL values
   *
   * @return the list of values
   */
  public List<Buffer> getCrlValues() {
    return crlValues;
  }

  /**
   * Add a CRL value
   *
   * @param crlValue  the value
   * @return a reference to this, so the API can be used fluently
   * @throws NullPointerException
   */
  public SSLOptions addCrlValue(Buffer crlValue) throws NullPointerException {
    Objects.requireNonNull(crlValue, "No null crl accepted");
    crlValues.add(crlValue);
    return this;
  }

  /**
   * @return whether to use or not Application-Layer Protocol Negotiation
   */
  public boolean isUseAlpn() {
    return useAlpn;
  }

  /**
   * Set the ALPN usage.
   *
   * @param useAlpn true when Application-Layer Protocol Negotiation should be used
   */
  public SSLOptions setUseAlpn(boolean useAlpn) {
    this.useAlpn = useAlpn;
    return this;
  }

  /**
   * @return whether to use or not HTTP3
   */
  public boolean isHttp3() {
    return http3;
  }

  /**
   * Set the http3 usage.
   *
   * @param http3 true when http3 should be used
   */
  public SSLOptions setHttp3(boolean http3) {
    this.http3 = http3;
    return this;
  }

  /**
   * @return get HTTP/3 Initial Max Streams Bidirectional count
   */
  public long getInitialMaxStreamsBidirectional() {
    return initialMaxStreamsBidirectional;
  }

  /**
   * Set the HTTP/3 Initial Max Streams Bidirectional count.
   *
   * @param initialMaxStreamsBidirectional the initial max streams bidirectional count
   */
  public SSLOptions setInitialMaxStreamsBidirectional(long initialMaxStreamsBidirectional) {
    this.initialMaxStreamsBidirectional = initialMaxStreamsBidirectional;
    return this;
  }

  /**
   * Returns the enabled SSL/TLS protocols
   * @return the enabled protocols
   */
  public Set<String> getEnabledSecureTransportProtocols() {
    return new LinkedHashSet<>(enabledSecureTransportProtocols);
  }

  /**
   * @return the SSL handshake timeout, in time unit specified by {@link #getSslHandshakeTimeoutUnit()}.
   */
  public long getSslHandshakeTimeout() {
    return sslHandshakeTimeout;
  }

  /**
   * Set the SSL handshake timeout, default time unit is seconds.
   *
   * @param sslHandshakeTimeout the SSL handshake timeout to set, in milliseconds
   * @return a reference to this, so the API can be used fluently
   */
  public SSLOptions setSslHandshakeTimeout(long sslHandshakeTimeout) {
    if (sslHandshakeTimeout < 0) {
      throw new IllegalArgumentException("sslHandshakeTimeout must be >= 0");
    }
    this.sslHandshakeTimeout = sslHandshakeTimeout;
    return this;
  }

  /**
   * Set the SSL handshake timeout unit. If not specified, default is seconds.
   *
   * @param sslHandshakeTimeoutUnit specify time unit.
   * @return a reference to this, so the API can be used fluently
   */
  public SSLOptions setSslHandshakeTimeoutUnit(TimeUnit sslHandshakeTimeoutUnit) {
    this.sslHandshakeTimeoutUnit = sslHandshakeTimeoutUnit;
    return this;
  }

  /**
   * @return the SSL handshake timeout unit.
   */
  public TimeUnit getSslHandshakeTimeoutUnit() {
    return sslHandshakeTimeoutUnit;
  }

  /**
   * Sets the list of enabled SSL/TLS protocols.
   *
   * @param enabledSecureTransportProtocols  the SSL/TLS protocols to enable
   * @return a reference to this, so the API can be used fluently
   */
  public SSLOptions setEnabledSecureTransportProtocols(Set<String> enabledSecureTransportProtocols) {
    this.enabledSecureTransportProtocols = enabledSecureTransportProtocols;
    return this;
  }

  /**
   * Add an enabled SSL/TLS protocols, appended to the ordered protocols.
   *
   * @param protocol  the SSL/TLS protocol to enable
   * @return a reference to this, so the API can be used fluently
   */
  public SSLOptions addEnabledSecureTransportProtocol(String protocol) {
    enabledSecureTransportProtocols.add(protocol);
    return this;
  }

  /**
   * Removes an enabled SSL/TLS protocol from the ordered protocols.
   *
   * @param protocol the SSL/TLS protocol to disable
   * @return a reference to this, so the API can be used fluently
   */
  public SSLOptions removeEnabledSecureTransportProtocol(String protocol) {
    enabledSecureTransportProtocols.remove(protocol);
    return this;
  }

  /**
   * @return the list of application-layer protocols send during the Application-Layer Protocol Negotiation.
   */
  public List<String> getApplicationLayerProtocols() {
    return applicationLayerProtocols;
  }

  /**
   * Set the list of application-layer protocols to provide to the server during the Application-Layer Protocol Negotiation.
   *
   * @param protocols the protocols
   * @return a reference to this, so the API can be used fluently
   */
  public SSLOptions setApplicationLayerProtocols(List<String> protocols) {
    this.applicationLayerProtocols = protocols;
    return this;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof SSLOptions) {
      SSLOptions that = (SSLOptions) obj;
      return sslHandshakeTimeoutUnit.toNanos(sslHandshakeTimeout) == that.sslHandshakeTimeoutUnit.toNanos(that.sslHandshakeTimeout) &&
         Objects.equals(keyCertOptions, that.keyCertOptions) &&
         Objects.equals(trustOptions, that.trustOptions) &&
         Objects.equals(enabledCipherSuites, that.enabledCipherSuites) &&
         Objects.equals(crlPaths, that.crlPaths) &&
         Objects.equals(crlValues, that.crlValues) &&
         useAlpn == that.useAlpn &&
         http3 == that.http3 &&
         Objects.equals(enabledSecureTransportProtocols, that.enabledSecureTransportProtocols);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(sslHandshakeTimeoutUnit.toNanos(sslHandshakeTimeout), keyCertOptions, trustOptions, enabledCipherSuites, crlPaths, crlValues, useAlpn, enabledSecureTransportProtocols, http3);
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    SSLOptionsConverter.toJson(this, json);
    return json;
  }
}
