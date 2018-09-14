/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Base class. TCP and SSL related options
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true, publicConverter = false)
public abstract class TCPSSLOptions extends NetworkOptions {

  /**
   * The default value of TCP-no-delay = true (Nagle disabled)
   */
  public static final boolean DEFAULT_TCP_NO_DELAY = true;

  /**
   * The default value of TCP keep alive = false
   */
  public static final boolean DEFAULT_TCP_KEEP_ALIVE = false;

  /**
   * The default value of SO_linger = -1
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

  /**
   * Default idle time unit = SECONDS
   */
  public static final TimeUnit DEFAULT_IDLE_TIMEOUT_TIME_UNIT = TimeUnit.SECONDS;

  /**
   * Default use alpn = false
   */
  public static final boolean DEFAULT_USE_ALPN = false;

  /**
   * The default SSL engine options = null (autoguess)
   */
  public static final SSLEngineOptions DEFAULT_SSL_ENGINE = null;

  /**
   * The default ENABLED_SECURE_TRANSPORT_PROTOCOLS value = { "SSLv2Hello", "TLSv1", "TLSv1.1", "TLSv1.2" }
   * <p/>
   * SSLv3 is NOT enabled due to POODLE vulnerability http://en.wikipedia.org/wiki/POODLE
   * <p/>
   * "SSLv2Hello" is NOT enabled since it's disabled by default since JDK7
   */
  public static final List<String> DEFAULT_ENABLED_SECURE_TRANSPORT_PROTOCOLS = Collections.unmodifiableList(Arrays.asList("TLSv1", "TLSv1.1", "TLSv1.2"));

  /**
   * The default TCP_FASTOPEN value = false
   */
  public static final boolean DEFAULT_TCP_FAST_OPEN = false;

  /**
   * The default TCP_CORK value = false
   */
  public static final boolean DEFAULT_TCP_CORK = false;

  /**
   * The default TCP_QUICKACK value = false
   */
  public static final boolean DEFAULT_TCP_QUICKACK = false;

  private boolean tcpNoDelay;
  private boolean tcpKeepAlive;
  private int soLinger;
  private boolean usePooledBuffers;
  private int idleTimeout;
  private TimeUnit idleTimeoutUnit;
  private boolean ssl;
  private KeyCertOptions keyCertOptions;
  private TrustOptions trustOptions;
  private Set<String> enabledCipherSuites;
  private ArrayList<String> crlPaths;
  private ArrayList<Buffer> crlValues;
  private boolean useAlpn;
  private SSLEngineOptions sslEngineOptions;
  private Set<String> enabledSecureTransportProtocols;
  private boolean tcpFastOpen;
  private boolean tcpCork;
  private boolean tcpQuickAck;

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
    this.idleTimeoutUnit = other.getIdleTimeoutUnit() != null ? other.getIdleTimeoutUnit() : DEFAULT_IDLE_TIMEOUT_TIME_UNIT;
    this.ssl = other.isSsl();
    this.keyCertOptions = other.getKeyCertOptions() != null ? other.getKeyCertOptions().clone() : null;
    this.trustOptions = other.getTrustOptions() != null ? other.getTrustOptions().clone() : null;
    this.enabledCipherSuites = other.getEnabledCipherSuites() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(other.getEnabledCipherSuites());
    this.crlPaths = new ArrayList<>(other.getCrlPaths());
    this.crlValues = new ArrayList<>(other.getCrlValues());
    this.useAlpn = other.useAlpn;
    this.sslEngineOptions = other.sslEngineOptions != null ? other.sslEngineOptions.clone() : null;
    this.enabledSecureTransportProtocols = other.getEnabledSecureTransportProtocols() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(other.getEnabledSecureTransportProtocols());
    this.tcpFastOpen = other.isTcpFastOpen();
    this.tcpCork = other.isTcpCork();
    this.tcpQuickAck = other.isTcpQuickAck();
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

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = super.toJson();
    TCPSSLOptionsConverter.toJson(this, json);
    return json;
  }

  private void init() {
    tcpNoDelay = DEFAULT_TCP_NO_DELAY;
    tcpKeepAlive = DEFAULT_TCP_KEEP_ALIVE;
    soLinger = DEFAULT_SO_LINGER;
    usePooledBuffers = DEFAULT_USE_POOLED_BUFFERS;
    idleTimeout = DEFAULT_IDLE_TIMEOUT;
    idleTimeoutUnit = DEFAULT_IDLE_TIMEOUT_TIME_UNIT;
    ssl = DEFAULT_SSL;
    enabledCipherSuites = new LinkedHashSet<>();
    crlPaths = new ArrayList<>();
    crlValues = new ArrayList<>();
    useAlpn = DEFAULT_USE_ALPN;
    sslEngineOptions = DEFAULT_SSL_ENGINE;
    enabledSecureTransportProtocols = new LinkedHashSet<>(DEFAULT_ENABLED_SECURE_TRANSPORT_PROTOCOLS);
    tcpFastOpen = DEFAULT_TCP_FAST_OPEN;
    tcpCork = DEFAULT_TCP_CORK;
    tcpQuickAck = DEFAULT_TCP_QUICKACK;
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
    if (soLinger < 0 && soLinger != DEFAULT_SO_LINGER) {
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
   * Set the idle timeout, default time unit is seconds. Zero means don't timeout.
   * This determines if a connection will timeout and be closed if no data is received within the timeout.
   *
   * If you want change default time unit, use {@link #setIdleTimeoutUnit(TimeUnit)}
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
   * @return the idle timeout, in time unit specified by {@link #getIdleTimeoutUnit()}.
   */
  public int getIdleTimeout() {
    return idleTimeout;
  }

  /**
   * Set the idle timeout unit. If not specified, default is seconds.
   *
   * @param idleTimeoutUnit specify time unit.
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setIdleTimeoutUnit(TimeUnit idleTimeoutUnit) {
    this.idleTimeoutUnit = idleTimeoutUnit;
    return this;
  }

  /**
   * @return the idle timeout unit.
   */
  public TimeUnit getIdleTimeoutUnit() {
    return idleTimeoutUnit;
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
  public TCPSSLOptions setKeyCertOptions(KeyCertOptions options) {
    this.keyCertOptions = options;
    return this;
  }

  /**
   * Get the key/cert options in jks format, aka Java keystore.
   *
   * @return the key/cert options in jks format, aka Java keystore.
   */
  public JksOptions getKeyStoreOptions() {
    return keyCertOptions instanceof JksOptions ? (JksOptions) keyCertOptions : null;
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
   * Set options in pkcs#11 format, aka Java keystore.
   * @param options the key store in pkcs#11 format
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setPkcs11KeyOptions(Pkcs11Options options) {
    this.keyCertOptions = options;
    return this;
  }

  /**
   * Get the key/cert options in pfx format.
   *
   * @return the key/cert options in pfx format.
   */
  public PfxOptions getPfxKeyCertOptions() {
    return keyCertOptions instanceof PfxOptions ? (PfxOptions) keyCertOptions : null;
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
   * Get the key/cert store options in pem format.
   *
   * @return the key/cert store options in pem format.
   */
  public PemKeyCertOptions getPemKeyCertOptions() {
    return keyCertOptions instanceof PemKeyCertOptions ? (PemKeyCertOptions) keyCertOptions : null;
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
   * Set the trust options.
   * @param options the trust options
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setTrustOptions(TrustOptions options) {
    this.trustOptions = options;
    return this;
  }

  /**
   * Get the trust options in jks format, aka Java truststore
   *
   * @return the trust options in jks format, aka Java truststore
   */
  public JksOptions getTrustStoreOptions() {
    return trustOptions instanceof JksOptions ? (JksOptions) trustOptions : null;
  }

  /**
   * Set the trust options in jks format, aka Java truststore
   * @param options the trust options in jks format
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setTrustStoreOptions(JksOptions options) {
    this.trustOptions = options;
    return this;
  }

  /**
   * Get the trust options in pfx format
   *
   * @return the trust options in pfx format
   */
  public PfxOptions getPfxTrustOptions() {
    return trustOptions instanceof PfxOptions ? (PfxOptions) trustOptions : null;
  }

  /**
   * Set the trust options in pfx format
   * @param options the trust options in pfx format
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setPfxTrustOptions(PfxOptions options) {
    this.trustOptions = options;
    return this;
  }

  /**
   * Get the trust options in pem format
   *
   * @return the trust options in pem format
   */
  public PemTrustOptions getPemTrustOptions() {
    return trustOptions instanceof PemTrustOptions ? (PemTrustOptions) trustOptions : null;
  }

  /**
   * Set the trust options in pem format
   * @param options the trust options in pem format
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setPemTrustOptions(PemTrustOptions options) {
    this.trustOptions = options;
    return this;
  }

  /**
   * Add an enabled cipher suite, appended to the ordered suites.
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
  public TCPSSLOptions setUseAlpn(boolean useAlpn) {
    this.useAlpn = useAlpn;
    return this;
  }

  /**
   * @return the SSL engine implementation to use
   */
  public SSLEngineOptions getSslEngineOptions() {
    return sslEngineOptions;
  }

  /**
   * Set to use SSL engine implementation to use.
   *
   * @param sslEngineOptions the ssl engine to use
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
    this.sslEngineOptions = sslEngineOptions;
    return this;
  }

  public JdkSSLEngineOptions getJdkSslEngineOptions() {
    return sslEngineOptions instanceof JdkSSLEngineOptions ? (JdkSSLEngineOptions) sslEngineOptions : null;
  }

  public TCPSSLOptions setJdkSslEngineOptions(JdkSSLEngineOptions sslEngineOptions) {
    return setSslEngineOptions(sslEngineOptions);
  }

  public OpenSSLEngineOptions getOpenSslEngineOptions() {
    return sslEngineOptions instanceof OpenSSLEngineOptions ? (OpenSSLEngineOptions) sslEngineOptions : null;
  }

  public TCPSSLOptions setOpenSslEngineOptions(OpenSSLEngineOptions sslEngineOptions) {
    return setSslEngineOptions(sslEngineOptions);
  }

  /**
   * Sets the list of enabled SSL/TLS protocols.
   *
   * @param enabledSecureTransportProtocols  the SSL/TLS protocols to enable
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setEnabledSecureTransportProtocols(Set<String> enabledSecureTransportProtocols) {
    this.enabledSecureTransportProtocols = enabledSecureTransportProtocols;
    return this;
  }

  /**
   * Add an enabled SSL/TLS protocols, appended to the ordered protocols.
   *
   * @param protocol  the SSL/TLS protocol to enable
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions addEnabledSecureTransportProtocol(String protocol) {
    enabledSecureTransportProtocols.add(protocol);
    return this;
  }

  /**
   * Removes an enabled SSL/TLS protocol from the ordered protocols.
   *
   * @param protocol the SSL/TLS protocol to disable
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions removeEnabledSecureTransportProtocol(String protocol) {
    enabledSecureTransportProtocols.remove(protocol);
    return this;
  }

  /**
   * @return wether {@code TCP_FASTOPEN} option is enabled
   */
  public boolean isTcpFastOpen() {
    return tcpFastOpen;
  }

  /**
   * Enable the {@code TCP_FASTOPEN} option - only with linux native transport.
   *
   * @param tcpFastOpen the fast open value
   */
  public TCPSSLOptions setTcpFastOpen(boolean tcpFastOpen) {
    this.tcpFastOpen = tcpFastOpen;
    return this;
  }

  /**
   * @return wether {@code TCP_CORK} option is enabled
   */
  public boolean isTcpCork() {
    return tcpCork;
  }

  /**
   * Enable the {@code TCP_CORK} option - only with linux native transport.
   *
   * @param tcpCork the cork value
   */
  public TCPSSLOptions setTcpCork(boolean tcpCork) {
    this.tcpCork = tcpCork;
    return this;
  }

  /**
   * @return wether {@code TCP_QUICKACK} option is enabled
   */
  public boolean isTcpQuickAck() {
    return tcpQuickAck;
  }

  /**
   * Enable the {@code TCP_QUICKACK} option - only with linux native transport.
   *
   * @param tcpQuickAck the quick ack value
   */
  public TCPSSLOptions setTcpQuickAck(boolean tcpQuickAck) {
    this.tcpQuickAck = tcpQuickAck;
    return this;
  }

  /**
   * Returns the enabled SSL/TLS protocols
   * @return the enabled protocols
   */
  public Set<String> getEnabledSecureTransportProtocols() {
    return new LinkedHashSet<>(enabledSecureTransportProtocols);
  }

  @Override
  public TCPSSLOptions setLogActivity(boolean logEnabled) {
    return (TCPSSLOptions) super.setLogActivity(logEnabled);
  }

  @Override
  public TCPSSLOptions setSendBufferSize(int sendBufferSize) {
    return (TCPSSLOptions) super.setSendBufferSize(sendBufferSize);
  }

  @Override
  public TCPSSLOptions setReceiveBufferSize(int receiveBufferSize) {
    return (TCPSSLOptions) super.setReceiveBufferSize(receiveBufferSize);
  }

  @Override
  public TCPSSLOptions setReuseAddress(boolean reuseAddress) {
    return (TCPSSLOptions) super.setReuseAddress(reuseAddress);
  }

  @Override
  public TCPSSLOptions setTrafficClass(int trafficClass) {
    return (TCPSSLOptions) super.setTrafficClass(trafficClass);
  }

  @Override
  public TCPSSLOptions setReusePort(boolean reusePort) {
    return (TCPSSLOptions) super.setReusePort(reusePort);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TCPSSLOptions)) return false;
    if (!super.equals(o)) return false;

    TCPSSLOptions that = (TCPSSLOptions) o;

    if (idleTimeout != that.idleTimeout) return false;
    if (idleTimeoutUnit != null ? !idleTimeoutUnit.equals(that.idleTimeoutUnit) : that.idleTimeoutUnit != null) return false;
    if (soLinger != that.soLinger) return false;
    if (ssl != that.ssl) return false;
    if (tcpKeepAlive != that.tcpKeepAlive) return false;
    if (tcpNoDelay != that.tcpNoDelay) return false;
    if (tcpFastOpen != that.tcpFastOpen) return false;
    if (tcpQuickAck != that.tcpQuickAck) return false;
    if (tcpCork != that.tcpCork) return false;
    if (usePooledBuffers != that.usePooledBuffers) return false;
    if (crlPaths != null ? !crlPaths.equals(that.crlPaths) : that.crlPaths != null) return false;
    if (crlValues != null ? !crlValues.equals(that.crlValues) : that.crlValues != null) return false;
    if (enabledCipherSuites != null ? !enabledCipherSuites.equals(that.enabledCipherSuites) : that.enabledCipherSuites != null)
      return false;
    if (keyCertOptions != null ? !keyCertOptions.equals(that.keyCertOptions) : that.keyCertOptions != null) return false;
    if (trustOptions != null ? !trustOptions.equals(that.trustOptions) : that.trustOptions != null) return false;
    if (useAlpn != that.useAlpn) return false;
    if (sslEngineOptions != null ? !sslEngineOptions.equals(that.sslEngineOptions) : that.sslEngineOptions != null) return false;
    if (!enabledSecureTransportProtocols.equals(that.enabledSecureTransportProtocols)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (tcpNoDelay ? 1 : 0);
    result = 31 * result + (tcpFastOpen ? 1 : 0);
    result = 31 * result + (tcpCork ? 1 : 0);
    result = 31 * result + (tcpQuickAck ? 1 : 0);
    result = 31 * result + (tcpKeepAlive ? 1 : 0);
    result = 31 * result + soLinger;
    result = 31 * result + (usePooledBuffers ? 1 : 0);
    result = 31 * result + idleTimeout;
    result = 31 * result + (idleTimeoutUnit != null ? idleTimeoutUnit.hashCode() : 0);
    result = 31 * result + (ssl ? 1 : 0);
    result = 31 * result + (keyCertOptions != null ? keyCertOptions.hashCode() : 0);
    result = 31 * result + (trustOptions != null ? trustOptions.hashCode() : 0);
    result = 31 * result + (enabledCipherSuites != null ? enabledCipherSuites.hashCode() : 0);
    result = 31 * result + (crlPaths != null ? crlPaths.hashCode() : 0);
    result = 31 * result + (crlValues != null ? crlValues.hashCode() : 0);
    result = 31 * result + (useAlpn ? 1 : 0);
    result = 31 * result + (sslEngineOptions != null ? sslEngineOptions.hashCode() : 0);
    result = 31 * result + (enabledSecureTransportProtocols != null ? enabledSecureTransportProtocols
        .hashCode() : 0);
    return result;
  }
}
