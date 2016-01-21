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

package io.vertx.core.net;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * Base class. TCP and SSL related options
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true)
public abstract class TCPSSLOptions extends NetworkOptions {

  /**
   * The default value of TCP-no-delay = true (Nagle disabled)
   */
  public static final boolean DEFAULT_TCP_NO_DELAY = true;

  /**
   * The default value of TCP keep alive
   */
  public static final boolean DEFAULT_TCP_KEEP_ALIVE = false;

  /**
   * The default value of SO_linger
   */
  public static final int DEFAULT_SO_LINGER = -1;

  /**
   * The default value of Netty use pooled buffers = false
   */
  public static final boolean DEFAULT_USE_POOLED_BUFFERS = false;

  /**
   * SSL enable by default = false
   */
  public static final boolean DEFAULT_SSL = false;

  /**
   * Default idle timeout = 0
   */
  public static final int DEFAULT_IDLE_TIMEOUT = 0;

  private boolean tcpNoDelay;
  private boolean tcpKeepAlive;
  private int soLinger;
  private boolean usePooledBuffers;
  private int idleTimeout;
  private boolean ssl;
  private KeyCertOptions keyCertOptions;
  private TrustOptions trustOptions;
  private Set<String> enabledCipherSuites = new HashSet<>();
  private ArrayList<String> crlPaths;
  private ArrayList<Buffer> crlValues;
  private Set<String> enabledSecureTransportProtocols = new HashSet<>();

  /**
   * Default constructor
   */
  public TCPSSLOptions() {
    super();
    init();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public TCPSSLOptions(TCPSSLOptions other) {
    super(other);
    this.tcpNoDelay = other.isTcpNoDelay();
    this.tcpKeepAlive = other.isTcpKeepAlive();
    this.soLinger = other.getSoLinger();
    this.usePooledBuffers = other.isUsePooledBuffers();
    this.idleTimeout = other.getIdleTimeout();
    this.ssl = other.isSsl();
    this.keyCertOptions = other.getKeyCertOptions() != null ? other.getKeyCertOptions().clone() : null;
    this.trustOptions = other.getTrustOptions() != null ? other.getTrustOptions().clone() : null;
    this.enabledCipherSuites = other.getEnabledCipherSuites() == null ? new HashSet<>() : new HashSet<>(other.getEnabledCipherSuites());
    this.crlPaths = new ArrayList<>(other.getCrlPaths());
    this.crlValues = new ArrayList<>(other.getCrlValues());
    this.enabledSecureTransportProtocols = other.getEnabledSecureTransportProtocols() == null ? new HashSet<>() : new HashSet<>(other.getEnabledSecureTransportProtocols());
  }

  /**
   * Create options from JSON
   *
   * @param json the JSON
   */
  public TCPSSLOptions(JsonObject json) {
    super(json);
    init();
    TCPSSLOptionsConverter.fromJson(json ,this);
  }

  private void init() {
    tcpNoDelay = DEFAULT_TCP_NO_DELAY;
    tcpKeepAlive = DEFAULT_TCP_KEEP_ALIVE;
    soLinger = DEFAULT_SO_LINGER;
    usePooledBuffers = DEFAULT_USE_POOLED_BUFFERS;
    idleTimeout = DEFAULT_IDLE_TIMEOUT;
    ssl = DEFAULT_SSL;
    crlPaths = new ArrayList<>();
    crlValues = new ArrayList<>();
  }

  /**
   * @return TCP no delay enabled ?
   */
  public boolean isTcpNoDelay() {
    return tcpNoDelay;
  }

  /**
   * Set whether TCP no delay is enabled
   *
   * @param tcpNoDelay true if TCP no delay is enabled (Nagle disabled)
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setTcpNoDelay(boolean tcpNoDelay) {
    this.tcpNoDelay = tcpNoDelay;
    return this;
  }

  /**
   * @return is TCP keep alive enabled?
   */
  public boolean isTcpKeepAlive() {
    return tcpKeepAlive;
  }

  /**
   * Set whether TCP keep alive is enabled
   *
   * @param tcpKeepAlive true if TCP keep alive is enabled
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setTcpKeepAlive(boolean tcpKeepAlive) {
    this.tcpKeepAlive = tcpKeepAlive;
    return this;
  }

  /**
   *
   * @return is SO_linger enabled
   */
  public int getSoLinger() {
    return soLinger;
  }

  /**
   * Set whether SO_linger keep alive is enabled
   *
   * @param soLinger true if SO_linger is enabled
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setSoLinger(int soLinger) {
    if (soLinger < 0) {
      throw new IllegalArgumentException("soLinger must be >= 0");
    }
    this.soLinger = soLinger;
    return this;
  }

  /**
   * @return are Netty pooled buffers enabled?
   *
   */
  public boolean isUsePooledBuffers() {
    return usePooledBuffers;
  }

  /**
   * Set whether Netty pooled buffers are enabled
   *
   * @param usePooledBuffers true if pooled buffers enabled
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setUsePooledBuffers(boolean usePooledBuffers) {
    this.usePooledBuffers = usePooledBuffers;
    return this;
  }

  /**
   * Set the idle timeout, in seconds. zero means don't timeout.
   * This determines if a connection will timeout and be closed if no data is received within the timeout.
   *
   * @param idleTimeout  the timeout, in seconds
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setIdleTimeout(int idleTimeout) {
    if (idleTimeout < 0) {
      throw new IllegalArgumentException("idleTimeout must be >= 0");
    }
    this.idleTimeout = idleTimeout;
    return this;
  }

  /**
   * @return  the idle timeout, in seconds
   */
  public int getIdleTimeout() {
    return idleTimeout;
  }

  /**
   *
   * @return is SSL/TLS enabled?
   */
  public boolean isSsl() {
    return ssl;
  }

  /**
   * Set whether SSL/TLS is enabled
   *
   * @param ssl  true if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setSsl(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  /**
   * @return the key cert options
   */
  public KeyCertOptions getKeyCertOptions() {
    return keyCertOptions;
  }

  /**
   * Set the key/cert options in jks format, aka Java keystore.
   * @param options the key store in jks format
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setKeyStoreOptions(JksOptions options) {
    this.keyCertOptions = options;
    return this;
  }

  /**
   * Set the key/cert options in pfx format.
   * @param options the key cert options in pfx format
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setPfxKeyCertOptions(PfxOptions options) {
    this.keyCertOptions = options;
    return this;
  }

  /**
   * Set the key/cert store options in pem format.
   * @param options the options in pem format
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setPemKeyCertOptions(PemKeyCertOptions options) {
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
   * Set the trust options in jks format, aka Java trustore
   * @param options the options in jks format
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setTrustStoreOptions(JksOptions options) {
    this.trustOptions = options;
    return this;
  }

  /**
   * Set the trust options in pfx format
   * @param options the options in pfx format
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setPfxTrustOptions(PfxOptions options) {
    this.trustOptions = options;
    return this;
  }

  /**
   * Set the trust options in pem format
   * @param options the options in pem format
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setPemTrustOptions(PemTrustOptions options) {
    this.trustOptions = options;
    return this;
  }

  /**
   * Add an enabled cipher suite
   *
   * @param suite  the suite
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions addEnabledCipherSuite(String suite) {
    enabledCipherSuites.add(suite);
    return this;
  }

  /**
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
  public TCPSSLOptions addCrlPath(String crlPath) throws NullPointerException {
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
  public TCPSSLOptions addCrlValue(Buffer crlValue) throws NullPointerException {
    Objects.requireNonNull(crlValue, "No null crl accepted");
    crlValues.add(crlValue);
    return this;
  }

  /**
   * Add an enabled SSL/TLS protocols
   *
   * @param protocol  the SSL/TLS protocol do enabled
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions addEnabledSecureTransportProtocol(String protocol) {
    enabledSecureTransportProtocols.add(protocol);
    return this;
  }

  /**
   * Returns the enabled SSL/TLS protocols
   * @return the enabled protocols
   */
  public Set<String> getEnabledSecureTransportProtocols() {
    return enabledSecureTransportProtocols;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TCPSSLOptions)) return false;
    if (!super.equals(o)) return false;

    TCPSSLOptions that = (TCPSSLOptions) o;

    if (idleTimeout != that.idleTimeout) return false;
    if (soLinger != that.soLinger) return false;
    if (ssl != that.ssl) return false;
    if (tcpKeepAlive != that.tcpKeepAlive) return false;
    if (tcpNoDelay != that.tcpNoDelay) return false;
    if (usePooledBuffers != that.usePooledBuffers) return false;
    if (crlPaths != null ? !crlPaths.equals(that.crlPaths) : that.crlPaths != null) return false;
    if (crlValues != null ? !crlValues.equals(that.crlValues) : that.crlValues != null) return false;
    if (enabledCipherSuites != null ? !enabledCipherSuites.equals(that.enabledCipherSuites) : that.enabledCipherSuites != null)
      return false;
    if (keyCertOptions != null ? !keyCertOptions.equals(that.keyCertOptions) : that.keyCertOptions != null) return false;
    if (trustOptions != null ? !trustOptions.equals(that.trustOptions) : that.trustOptions != null) return false;
    if (!enabledSecureTransportProtocols.equals(that.enabledSecureTransportProtocols)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (tcpNoDelay ? 1 : 0);
    result = 31 * result + (tcpKeepAlive ? 1 : 0);
    result = 31 * result + soLinger;
    result = 31 * result + (usePooledBuffers ? 1 : 0);
    result = 31 * result + idleTimeout;
    result = 31 * result + (ssl ? 1 : 0);
    result = 31 * result + (keyCertOptions != null ? keyCertOptions.hashCode() : 0);
    result = 31 * result + (trustOptions != null ? trustOptions.hashCode() : 0);
    result = 31 * result + (enabledCipherSuites != null ? enabledCipherSuites.hashCode() : 0);
    result = 31 * result + (crlPaths != null ? crlPaths.hashCode() : 0);
    result = 31 * result + (crlValues != null ? crlValues.hashCode() : 0);
    result = 31 * result + (enabledSecureTransportProtocols != null ? enabledSecureTransportProtocols
        .hashCode() : 0);
    return result;
  }
}
