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

package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;
import io.netty.handler.logging.ByteBufFormat;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Base class. TCP and SSL related options
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
@JsonGen(publicConverter = false)
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
   * Default value for tcp keepalive idle time -1 defaults to OS settings
   */
  public static final int DEFAULT_TCP_KEEPALIVE_IDLE_SECONDS = -1;

  /**
   * Default value for tcp keepalive count -1 defaults to OS settings
   */
  public static final int DEFAULT_TCP_KEEPALIVE_COUNT = -1;

  /**
   * Default value for tcp keepalive interval -1 defaults to OS settings
   */
  public static final int DEFAULT_TCP_KEEAPLIVE_INTERVAL_SECONDS = -1;

  /**
   * The default value of SO_linger = -1
   */
  public static final int DEFAULT_SO_LINGER = -1;

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
   * Default read idle timeout = 0
   */
  public static final int DEFAULT_READ_IDLE_TIMEOUT = 0;

  /**
   * Default write idle timeout = 0
   */
  public static final int DEFAULT_WRITE_IDLE_TIMEOUT = 0;

  /**
   * See {@link SSLOptions#DEFAULT_USE_ALPN}
   */
  public static final boolean DEFAULT_USE_ALPN = SSLOptions.DEFAULT_USE_ALPN;

  /**
   * The default SSL engine options = null (autoguess)
   */
  public static final SSLEngineOptions DEFAULT_SSL_ENGINE = null;

  /**
   * See {@link SSLOptions#DEFAULT_ENABLED_SECURE_TRANSPORT_PROTOCOLS}
   */
  public static final List<String> DEFAULT_ENABLED_SECURE_TRANSPORT_PROTOCOLS = SSLOptions.DEFAULT_ENABLED_SECURE_TRANSPORT_PROTOCOLS;

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

  /**
   * The default TCP_USER_TIMEOUT value in milliseconds = 0
   * <p/>
   * When the default value of 0 is used, TCP will use the system default.
   */
  public static final int DEFAULT_TCP_USER_TIMEOUT = 0;

  /**
   * See {@link SSLOptions#DEFAULT_SSL_HANDSHAKE_TIMEOUT}
   */
  public static final long DEFAULT_SSL_HANDSHAKE_TIMEOUT = SSLOptions.DEFAULT_SSL_HANDSHAKE_TIMEOUT;

  /**
   * See {@link SSLOptions#DEFAULT_SSL_HANDSHAKE_TIMEOUT_TIME_UNIT}
   */
  public static final TimeUnit DEFAULT_SSL_HANDSHAKE_TIMEOUT_TIME_UNIT = SSLOptions.DEFAULT_SSL_HANDSHAKE_TIMEOUT_TIME_UNIT;

  private boolean tcpNoDelay;
  private boolean tcpKeepAlive;
  private int tcpKeepAliveIdleSeconds;
  private int tcpKeepAliveCount;
  private int tcpKeepAliveIntervalSeconds;
  private int soLinger;
  private int idleTimeout;
  private int readIdleTimeout;
  private int writeIdleTimeout;
  private TimeUnit idleTimeoutUnit;
  private boolean ssl;
  private SSLEngineOptions sslEngineOptions;
  private SSLOptions sslOptions;
  private boolean tcpFastOpen;
  private boolean tcpCork;
  private boolean tcpQuickAck;
  private int tcpUserTimeout;

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
    this.tcpKeepAliveIdleSeconds = other.getTcpKeepAliveIdleSeconds();
    this.tcpKeepAliveCount = other.getTcpKeepAliveCount();
    this.tcpKeepAliveIntervalSeconds = other.getTcpKeepAliveIntervalSeconds();
    this.soLinger = other.getSoLinger();
    this.idleTimeout = other.getIdleTimeout();
    this.idleTimeoutUnit = other.getIdleTimeoutUnit() != null ? other.getIdleTimeoutUnit() : DEFAULT_IDLE_TIMEOUT_TIME_UNIT;
    this.readIdleTimeout = other.getReadIdleTimeout();
    this.writeIdleTimeout = other.getWriteIdleTimeout();
    this.ssl = other.isSsl();
    this.sslEngineOptions = other.sslEngineOptions != null ? other.sslEngineOptions.copy() : null;
    this.tcpFastOpen = other.isTcpFastOpen();
    this.tcpCork = other.isTcpCork();
    this.tcpQuickAck = other.isTcpQuickAck();
    this.tcpUserTimeout = other.getTcpUserTimeout();
    this.sslOptions = new SSLOptions(other.sslOptions);
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
    tcpKeepAliveIdleSeconds = DEFAULT_TCP_KEEPALIVE_IDLE_SECONDS;
    tcpKeepAliveCount = DEFAULT_TCP_KEEPALIVE_COUNT;
    tcpKeepAliveIntervalSeconds = DEFAULT_TCP_KEEAPLIVE_INTERVAL_SECONDS;
    soLinger = DEFAULT_SO_LINGER;
    idleTimeout = DEFAULT_IDLE_TIMEOUT;
    readIdleTimeout = DEFAULT_READ_IDLE_TIMEOUT;
    writeIdleTimeout = DEFAULT_WRITE_IDLE_TIMEOUT;
    idleTimeoutUnit = DEFAULT_IDLE_TIMEOUT_TIME_UNIT;
    ssl = DEFAULT_SSL;
    sslEngineOptions = DEFAULT_SSL_ENGINE;
    tcpFastOpen = DEFAULT_TCP_FAST_OPEN;
    tcpCork = DEFAULT_TCP_CORK;
    tcpQuickAck = DEFAULT_TCP_QUICKACK;
    tcpUserTimeout = DEFAULT_TCP_USER_TIMEOUT;
    sslOptions = new SSLOptions();
  }

  @GenIgnore
  public SSLOptions getSslOptions() {
    return sslOptions;
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
   * @return the time in seconds the connection needs to remain idle before TCP starts sending keepalive probes
   */
  public int getTcpKeepAliveIdleSeconds() {
    return tcpKeepAliveIdleSeconds;
  }

  /**
   * The time in seconds the connection needs to remain idle before TCP starts sending keepalive probes,
   * if the socket option keepalive has been set.
   *
   * @param tcpKeepAliveIdleSeconds
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setTcpKeepAliveIdleSeconds(int tcpKeepAliveIdleSeconds) {
    Arguments.require(tcpKeepAliveIdleSeconds > 0 || tcpKeepAliveIdleSeconds == DEFAULT_TCP_KEEPALIVE_IDLE_SECONDS, "tcpKeepAliveIdleSeconds must be > 0");
    this.tcpKeepAliveIdleSeconds = tcpKeepAliveIdleSeconds;
    return this;
  }

  /**
   * @return the maximum number of keepalive probes TCP should send before dropping the connection.
   */
  public int getTcpKeepAliveCount() {
    return tcpKeepAliveCount;
  }

  /**
   * The maximum number of keepalive probes TCP should send before dropping the connection.
   * @param tcpKeepAliveCount
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setTcpKeepAliveCount(int tcpKeepAliveCount) {
    Arguments.require(tcpKeepAliveCount > 0 || tcpKeepAliveCount == DEFAULT_TCP_KEEPALIVE_COUNT, "tcpKeepAliveCount must be > 0");
    this.tcpKeepAliveCount = tcpKeepAliveCount;
    return this;
  }

  /**
   * @return the time in seconds between individual keepalive probes.
   */
  public int getTcpKeepAliveIntervalSeconds() {
    return tcpKeepAliveIntervalSeconds;
  }

  /**
   * The time in seconds between individual keepalive probes.
   * @param tcpKeepAliveIntervalSeconds
   * @return
   */
  public TCPSSLOptions setTcpKeepAliveIntervalSeconds(int tcpKeepAliveIntervalSeconds) {
    Arguments.require(tcpKeepAliveIntervalSeconds > 0 || tcpKeepAliveIntervalSeconds == DEFAULT_TCP_KEEAPLIVE_INTERVAL_SECONDS, "tcpKeepAliveIntervalSeconds must be > 0");
    this.tcpKeepAliveIntervalSeconds = tcpKeepAliveIntervalSeconds;
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
   * Set the idle timeout, default time unit is seconds. Zero means don't timeout.
   * This determines if a connection will timeout and be closed if no data is received nor sent within the timeout.
   *
   * If you want change default time unit, use {@link #setIdleTimeoutUnit(TimeUnit)}
   *
   * @param idleTimeout  the timeout
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
   * Set the read idle timeout, default time unit is seconds. Zero means don't timeout.
   * This determines if a connection will timeout and be closed if no data is received within the timeout.
   *
   * If you want change default time unit, use {@link #setIdleTimeoutUnit(TimeUnit)}
   *
   * @param idleTimeout  the read timeout
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setReadIdleTimeout(int idleTimeout) {
    if (idleTimeout < 0) {
      throw new IllegalArgumentException("readIdleTimeout must be >= 0");
    }
    this.readIdleTimeout = idleTimeout;
    return this;
  }

  /**
   * @return the read idle timeout, in time unit specified by {@link #getIdleTimeoutUnit()}.
   */
  public int getReadIdleTimeout() {
    return readIdleTimeout;
  }

  /**
   * Set the write idle timeout, default time unit is seconds. Zero means don't timeout.
   * This determines if a connection will timeout and be closed if no data is sent within the timeout.
   *
   * If you want change default time unit, use {@link #setIdleTimeoutUnit(TimeUnit)}
   *
   * @param idleTimeout  the write timeout
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setWriteIdleTimeout(int idleTimeout) {
    if (idleTimeout < 0) {
      throw new IllegalArgumentException("writeIdleTimeout must be >= 0");
    }
    this.writeIdleTimeout = idleTimeout;
    return this;
  }

  /**
   * @return the write idle timeout, in time unit specified by {@link #getIdleTimeoutUnit()}.
   */
  public int getWriteIdleTimeout() {
    return writeIdleTimeout;
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
    return sslOptions.getKeyCertOptions();
  }

  /**
   * Set the key/cert options.
   *
   * @param options the key store options
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public TCPSSLOptions setKeyCertOptions(KeyCertOptions options) {
    sslOptions.setKeyCertOptions(options);
    return this;
  }

  /**
   * Get the key/cert options in jks format, aka Java keystore.
   *
   * @return the key/cert options in jks format, aka Java keystore.
   * @deprecated instead use {@link #getKeyCertOptions()}
   */
  @Deprecated
  public JksOptions getKeyStoreOptions() {
    KeyCertOptions keyCertOptions = sslOptions.getKeyCertOptions();
    return keyCertOptions instanceof JksOptions ? (JksOptions) keyCertOptions : null;
  }

  /**
   * Set the key/cert options in jks format, aka Java keystore.
   * @param options the key store in jks format
   * @return a reference to this, so the API can be used fluently
   * @deprecated instead use {@link #setKeyCertOptions(KeyCertOptions)}
   */
  @Deprecated
  public TCPSSLOptions setKeyStoreOptions(JksOptions options) {
    return setKeyCertOptions(options);
  }

  /**
   * Get the key/cert options in pfx format.
   *
   * @return the key/cert options in pfx format.
   * @deprecated instead use {@link #getKeyCertOptions()}
   */
  @Deprecated
  public PfxOptions getPfxKeyCertOptions() {
    KeyCertOptions keyCertOptions = sslOptions.getKeyCertOptions();
    return keyCertOptions instanceof PfxOptions ? (PfxOptions) keyCertOptions : null;
  }

  /**
   * Set the key/cert options in pfx format.
   * @param options the key cert options in pfx format
   * @return a reference to this, so the API can be used fluently
   * @deprecated instead use {@link #setKeyCertOptions(KeyCertOptions)}
   */
  @Deprecated
  public TCPSSLOptions setPfxKeyCertOptions(PfxOptions options) {
    return setKeyCertOptions(options);
  }

  /**
   * Get the key/cert store options in pem format.
   *
   * @return the key/cert store options in pem format.
   * @deprecated instead use {@link #getKeyCertOptions()}
   */
  @Deprecated
  public PemKeyCertOptions getPemKeyCertOptions() {
    KeyCertOptions keyCertOptions = sslOptions.getKeyCertOptions();
    return keyCertOptions instanceof PemKeyCertOptions ? (PemKeyCertOptions) keyCertOptions : null;
  }

  /**
   * Set the key/cert store options in pem format.
   * @param options the options in pem format
   * @return a reference to this, so the API can be used fluently
   * @deprecated instead use {@link #setKeyCertOptions(KeyCertOptions)}
   */
  @Deprecated
  public TCPSSLOptions setPemKeyCertOptions(PemKeyCertOptions options) {
    return setKeyCertOptions(options);
  }

  /**
   * @return the trust options
   */
  public TrustOptions getTrustOptions() {
    return sslOptions.getTrustOptions();
  }

  /**
   * Set the trust options.
   * @param options the trust options
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setTrustOptions(TrustOptions options) {
    sslOptions.setTrustOptions(options);
    return this;
  }

  /**
   * Get the trust options in jks format, aka Java truststore
   *
   * @return the trust options in jks format, aka Java truststore
   * @deprecated instead use {@link #getTrustOptions()}
   */
  @Deprecated
  public JksOptions getTrustStoreOptions() {
    TrustOptions trustOptions = sslOptions.getTrustOptions();
    return trustOptions instanceof JksOptions ? (JksOptions) trustOptions : null;
  }

  /**
   * Set the trust options in jks format, aka Java truststore
   * @param options the trust options in jks format
   * @return a reference to this, so the API can be used fluently
   * @deprecated instead use {@link #setTrustOptions(TrustOptions)}
   */
  @Deprecated
  public TCPSSLOptions setTrustStoreOptions(JksOptions options) {
    return setTrustOptions(options);
  }

  /**
   * Get the trust options in pfx format
   *
   * @return the trust options in pfx format
   * @deprecated instead use {@link #getTrustOptions()}
   */
  @Deprecated
  public PfxOptions getPfxTrustOptions() {
    TrustOptions trustOptions = sslOptions.getTrustOptions();
    return trustOptions instanceof PfxOptions ? (PfxOptions) trustOptions : null;
  }

  /**
   * Set the trust options in pfx format
   * @param options the trust options in pfx format
   * @return a reference to this, so the API can be used fluently
   * @deprecated instead use {@link #setTrustOptions(TrustOptions)}
   */
  @Deprecated
  public TCPSSLOptions setPfxTrustOptions(PfxOptions options) {
    return setTrustOptions(options);
  }

  /**
   * Get the trust options in pem format
   *
   * @return the trust options in pem format
   * @deprecated instead use {@link #getTrustOptions()}
   */
  @Deprecated
  public PemTrustOptions getPemTrustOptions() {
    TrustOptions trustOptions = sslOptions.getTrustOptions();
    return trustOptions instanceof PemTrustOptions ? (PemTrustOptions) trustOptions : null;
  }

  /**
   * Set the trust options in pem format
   * @param options the trust options in pem format
   * @return a reference to this, so the API can be used fluently
   * @deprecated instead use {@link #setTrustOptions(TrustOptions)}
   */
  @Deprecated
  public TCPSSLOptions setPemTrustOptions(PemTrustOptions options) {
    return setTrustOptions(options);
  }

  /**
   * Add an enabled cipher suite, appended to the ordered suites.
   *
   * @param suite  the suite
   * @return a reference to this, so the API can be used fluently
   * @see #getEnabledCipherSuites()
   */
  public TCPSSLOptions addEnabledCipherSuite(String suite) {
    sslOptions.addEnabledCipherSuite(suite);
    return this;
  }

  /**
   * Removes an enabled cipher suite from the ordered suites.
   *
   * @param suite  the suite
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions removeEnabledCipherSuite(String suite) {
    sslOptions.removeEnabledCipherSuite(suite);
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
    return sslOptions.getEnabledCipherSuites();
  }

  /**
   *
   * @return the CRL (Certificate revocation list) paths
   */
  public List<String> getCrlPaths() {
    return sslOptions.getCrlPaths();
  }

  /**
   * Add a CRL path
   * @param crlPath  the path
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions addCrlPath(String crlPath) {
    sslOptions.addCrlPath(crlPath);
    return this;
  }

  /**
   * Get the CRL values
   *
   * @return the list of values
   */
  public List<Buffer> getCrlValues() {
    return sslOptions.getCrlValues();
  }

  /**
   * Add a CRL value
   *
   * @param crlValue  the value
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions addCrlValue(Buffer crlValue) {
    sslOptions.addCrlValue(crlValue);
    return this;
  }

  /**
   * @return whether to use or not Application-Layer Protocol Negotiation
   */
  public boolean isUseAlpn() {
    return sslOptions.isUseAlpn();
  }

  /**
   * Set the ALPN usage.
   *
   * @param useAlpn true when Application-Layer Protocol Negotiation should be used
   */
  public TCPSSLOptions setUseAlpn(boolean useAlpn) {
    sslOptions.setUseAlpn(useAlpn);
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

  /**
   * @deprecated instead use {@link #getSslEngineOptions()}
   */
  @Deprecated
  public JdkSSLEngineOptions getJdkSslEngineOptions() {
    return sslEngineOptions instanceof JdkSSLEngineOptions ? (JdkSSLEngineOptions) sslEngineOptions : null;
  }

  /**
   * @deprecated instead use {@link #setSslEngineOptions(SSLEngineOptions)}
   */
  @Deprecated
  public TCPSSLOptions setJdkSslEngineOptions(JdkSSLEngineOptions sslEngineOptions) {
    return setSslEngineOptions(sslEngineOptions);
  }

  /**
   * @deprecated instead use {@link #getSslEngineOptions()}
   */
  @Deprecated
  public OpenSSLEngineOptions getOpenSslEngineOptions() {
    return sslEngineOptions instanceof OpenSSLEngineOptions ? (OpenSSLEngineOptions) sslEngineOptions : null;
  }

  /**
   * @deprecated instead use {@link #setSslEngineOptions(SSLEngineOptions)}
   */
  @Deprecated
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
    sslOptions.setEnabledSecureTransportProtocols(enabledSecureTransportProtocols);
    return this;
  }

  /**
   * Add an enabled SSL/TLS protocols, appended to the ordered protocols.
   *
   * @param protocol  the SSL/TLS protocol to enable
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions addEnabledSecureTransportProtocol(String protocol) {
    sslOptions.addEnabledSecureTransportProtocol(protocol);
    return this;
  }

  /**
   * Removes an enabled SSL/TLS protocol from the ordered protocols.
   *
   * @param protocol the SSL/TLS protocol to disable
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions removeEnabledSecureTransportProtocol(String protocol) {
    sslOptions.removeEnabledSecureTransportProtocol(protocol);
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
   *
   * @return the {@code TCP_USER_TIMEOUT} value
   */
  public int getTcpUserTimeout() {
    return tcpUserTimeout;
  }

  /**
   * Sets the {@code TCP_USER_TIMEOUT} option - only with linux native transport.
   *
   * @param tcpUserTimeout the tcp user timeout value
   */
  public TCPSSLOptions setTcpUserTimeout(int tcpUserTimeout) {
    this.tcpUserTimeout = tcpUserTimeout;
    return this;
  }

  /**
   * Returns the enabled SSL/TLS protocols
   * @return the enabled protocols
   */
  public Set<String> getEnabledSecureTransportProtocols() {
    return sslOptions.getEnabledSecureTransportProtocols();
  }

  /**
   * @return the SSL handshake timeout, in time unit specified by {@link #getSslHandshakeTimeoutUnit()}.
   */
  public long getSslHandshakeTimeout() {
    return sslOptions.getSslHandshakeTimeout();
  }

  /**
   * Set the SSL handshake timeout, default time unit is seconds.
   *
   * @param sslHandshakeTimeout the SSL handshake timeout to set, in milliseconds
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setSslHandshakeTimeout(long sslHandshakeTimeout) {
    sslOptions.setSslHandshakeTimeout(sslHandshakeTimeout);
    return this;
  }

  /**
   * Set the SSL handshake timeout unit. If not specified, default is seconds.
   *
   * @param sslHandshakeTimeoutUnit specify time unit.
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setSslHandshakeTimeoutUnit(TimeUnit sslHandshakeTimeoutUnit) {
    sslOptions.setSslHandshakeTimeoutUnit(sslHandshakeTimeoutUnit);
    return this;
  }

  /**
   * @return the SSL handshake timeout unit.
   */
  public TimeUnit getSslHandshakeTimeoutUnit() {
    return sslOptions.getSslHandshakeTimeoutUnit();
  }

  @Override
  public TCPSSLOptions setLogActivity(boolean logEnabled) {
    return (TCPSSLOptions) super.setLogActivity(logEnabled);
  }

  @Override
  public TCPSSLOptions setActivityLogDataFormat(ByteBufFormat activityLogDataFormat) {
    return (TCPSSLOptions) super.setActivityLogDataFormat(activityLogDataFormat);
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

}
