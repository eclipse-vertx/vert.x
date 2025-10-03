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
@JsonGen(publicConverter = false, inheritConverter = true)
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
   * The default SSL engine options = null (autoguess)
   */
  public static final SSLEngineOptions DEFAULT_SSL_ENGINE = null;

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

  private TcpOptions transportOptions;
  private int idleTimeout;
  private int readIdleTimeout;
  private int writeIdleTimeout;
  private TimeUnit idleTimeoutUnit;
  private boolean ssl;
  private SSLEngineOptions sslEngineOptions;
  private SSLOptions sslOptions;

  private Set<String> enabledCipherSuites;
  private List<String> crlPaths;
  private List<Buffer> crlValues;

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
    this.idleTimeout = other.getIdleTimeout();
    this.idleTimeoutUnit = other.getIdleTimeoutUnit() != null ? other.getIdleTimeoutUnit() : DEFAULT_IDLE_TIMEOUT_TIME_UNIT;
    this.readIdleTimeout = other.getReadIdleTimeout();
    this.writeIdleTimeout = other.getWriteIdleTimeout();
    this.ssl = other.isSsl();
    this.sslEngineOptions = other.sslEngineOptions != null ? other.sslEngineOptions.copy() : null;
    this.transportOptions = other.transportOptions != null ? other.transportOptions.copy() : new TcpOptions();

    SSLOptions sslOptions = other.sslOptions;
    if (sslOptions != null) {
      this.sslOptions = sslOptions.copy();
      if (this.sslOptions != null) {
        enabledCipherSuites = this.sslOptions.enabledCipherSuites;
        crlPaths = this.sslOptions.crlPaths;
        crlValues = this.sslOptions.crlValues;
      }
    }
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
    // Legacy
    if (json.containsKey("pemKeyCertOptions")) {
      setKeyCertOptions(new PemKeyCertOptions(json.getJsonObject("pemKeyCertOptions")));
    } else if (json.containsKey("keyStoreOptions")) {
      setKeyCertOptions(new JksOptions(json.getJsonObject("keyStoreOptions")));
    } else if (json.containsKey("pfxKeyCertOptions")) {
      setKeyCertOptions(new PfxOptions(json.getJsonObject("pfxKeyCertOptions")));
    }
    if (json.containsKey("pemTrustOptions")) {
      setTrustOptions(new PemTrustOptions(json.getJsonObject("pemTrustOptions")));
    } else if (json.containsKey("pfxTrustOptions")) {
      setTrustOptions(new PfxOptions(json.getJsonObject("pfxTrustOptions")));
    } else if (json.containsKey("trustStoreOptions")) {
      setTrustOptions(new JksOptions(json.getJsonObject("trustStoreOptions")));
    }
    if (json.containsKey("jdkSslEngineOptions")) {
      setSslEngineOptions(new JdkSSLEngineOptions(json.getJsonObject("jdkSslEngineOptions")));
    } else if (json.containsKey("openSslEngineOptions")) {
      setSslEngineOptions(new OpenSSLEngineOptions(json.getJsonObject("openSslEngineOptions")));
    }
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    TCPSSLOptionsConverter.toJson(this, json);
    if (sslOptions != null) {
      KeyCertOptions keyCertOptions = sslOptions.getKeyCertOptions();
      if (keyCertOptions != null) {
        if (keyCertOptions instanceof PemKeyCertOptions) {
          json.put("pemKeyCertOptions", ((PemKeyCertOptions) keyCertOptions).toJson());
        } else if (keyCertOptions instanceof JksOptions) {
          json.put("keyStoreOptions", ((JksOptions) keyCertOptions).toJson());
        } else if (keyCertOptions instanceof PfxOptions) {
          json.put("pfxKeyCertOptions", ((PfxOptions) keyCertOptions).toJson());
        }
      }
      TrustOptions trustOptions = sslOptions.getTrustOptions();
      if (trustOptions instanceof PemTrustOptions) {
        json.put("pemTrustOptions", ((PemTrustOptions) trustOptions).toJson());
      } else if (trustOptions instanceof PfxOptions) {
        json.put("pfxTrustOptions", ((PfxOptions) trustOptions).toJson());
      } else if (trustOptions instanceof JksOptions) {
        json.put("trustStoreOptions", ((JksOptions) trustOptions).toJson());
      }
    }
    SSLEngineOptions engineOptions = sslEngineOptions;
    if (engineOptions != null) {
      if (engineOptions instanceof JdkSSLEngineOptions) {
        json.put("jdkSslEngineOptions", ((JdkSSLEngineOptions) engineOptions).toJson());
      } else if (engineOptions instanceof OpenSSLEngineOptions) {
        json.put("openSslEngineOptions", ((OpenSSLEngineOptions) engineOptions).toJson());
      }
    }
    return json;
  }

  private void init() {
    idleTimeout = DEFAULT_IDLE_TIMEOUT;
    readIdleTimeout = DEFAULT_READ_IDLE_TIMEOUT;
    writeIdleTimeout = DEFAULT_WRITE_IDLE_TIMEOUT;
    idleTimeoutUnit = DEFAULT_IDLE_TIMEOUT_TIME_UNIT;
    ssl = DEFAULT_SSL;
    sslEngineOptions = DEFAULT_SSL_ENGINE;
    transportOptions = new TcpOptions();
    sslOptions = null;
  }

  protected SSLOptions getOrCreateSSLOptions() {
    if (sslOptions == null) {
      sslOptions = this instanceof ClientOptionsBase ? new ClientSSLOptions() : new ServerSSLOptions();
      // Necessary hacks because we return lazy created collections so we need to care about that
      if (enabledCipherSuites != null) {
        sslOptions.enabledCipherSuites = enabledCipherSuites;
      } else {
        enabledCipherSuites = sslOptions.enabledCipherSuites;
      }
      if (crlPaths != null) {
        sslOptions.crlPaths = crlPaths;
      } else {
        crlPaths = sslOptions.crlPaths;
      }
      if (crlValues != null) {
        sslOptions.crlValues = crlValues;
      } else {
        crlValues = sslOptions.crlValues;
      }
    }
    return sslOptions;
  }

  @GenIgnore
  public TcpOptions getTransportOptions() {
    return transportOptions;
  }

  @GenIgnore
  public SSLOptions getSslOptions() {
    return sslOptions;
  }

  @Override
  public boolean isReusePort() {
    return transportOptions.isReusePort();
  }

  @Override
  public TCPSSLOptions setReusePort(boolean reusePort) {
    transportOptions.setReusePort(reusePort);
    return this;
  }

  /**
   * @return TCP no delay enabled ?
   */
  public boolean isTcpNoDelay() {
    return transportOptions.isTcpNoDelay();
  }

  /**
   * Set whether TCP no delay is enabled
   *
   * @param tcpNoDelay true if TCP no delay is enabled (Nagle disabled)
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setTcpNoDelay(boolean tcpNoDelay) {
    transportOptions.setTcpNoDelay(tcpNoDelay);
    return this;
  }

  /**
   * @return is TCP keep alive enabled?
   */
  public boolean isTcpKeepAlive() {
    return transportOptions.isTcpKeepAlive();
  }

  /**
   * Set whether TCP keep alive is enabled
   *
   * @param tcpKeepAlive true if TCP keep alive is enabled
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setTcpKeepAlive(boolean tcpKeepAlive) {
    transportOptions.setTcpKeepAlive(tcpKeepAlive);
    return this;
  }

  /**
   *
   * @return is SO_linger enabled
   */
  public int getSoLinger() {
    return transportOptions.getSoLinger();
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
    transportOptions.setSoLinger(soLinger);
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
    SSLOptions o = sslOptions;
    return o != null ? o.getKeyCertOptions() : null;
  }

  /**
   * Set the key/cert options.
   *
   * @param options the key store options
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public TCPSSLOptions setKeyCertOptions(KeyCertOptions options) {
    getOrCreateSSLOptions().setKeyCertOptions(options);
    return this;
  }

  /**
   * @return the trust options
   */
  public TrustOptions getTrustOptions() {
    SSLOptions o = sslOptions;
    return o != null ? o.getTrustOptions() : null;
  }

  /**
   * Set the trust options.
   * @param options the trust options
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setTrustOptions(TrustOptions options) {
    getOrCreateSSLOptions().setTrustOptions(options);
    return this;
  }

  /**
   * Add an enabled cipher suite, appended to the ordered suites.
   *
   * @param suite  the suite
   * @return a reference to this, so the API can be used fluently
   * @see #getEnabledCipherSuites()
   */
  public TCPSSLOptions addEnabledCipherSuite(String suite) {
    getOrCreateSSLOptions().addEnabledCipherSuite(suite);
    return this;
  }

  /**
   * Removes an enabled cipher suite from the ordered suites.
   *
   * @param suite  the suite
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions removeEnabledCipherSuite(String suite) {
    getOrCreateSSLOptions().removeEnabledCipherSuite(suite);
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
    if (enabledCipherSuites == null) {
      enabledCipherSuites = new LinkedHashSet<>();
    }
    return enabledCipherSuites;
  }

  /**
   *
   * @return the CRL (Certificate revocation list) paths
   */
  public List<String> getCrlPaths() {
    if (crlPaths == null) {
      crlPaths = new ArrayList<>();
    }
    return crlPaths;
  }

  /**
   * Add a CRL path
   * @param crlPath  the path
   * @return a reference to this, so the API can be used fluently
   * @throws NullPointerException
   */
  public TCPSSLOptions addCrlPath(String crlPath) throws NullPointerException {
    getOrCreateSSLOptions().addCrlPath(crlPath);
    return this;
  }

  /**
   * Get the CRL values
   *
   * @return the list of values
   */
  public List<Buffer> getCrlValues() {
    if (crlValues == null) {
      crlValues =  new ArrayList<>();
    }
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
    getOrCreateSSLOptions().addCrlValue(crlValue);
    return this;
  }

  /**
   * @return whether to use or not Application-Layer Protocol Negotiation
   */
  public boolean isUseAlpn() {
    SSLOptions o = sslOptions;
    return o != null && o.isUseAlpn();
  }

  /**
   * Set the ALPN usage.
   *
   * @param useAlpn true when Application-Layer Protocol Negotiation should be used
   */
  public TCPSSLOptions setUseAlpn(boolean useAlpn) {
    getOrCreateSSLOptions().setUseAlpn(useAlpn);
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
   * Sets the list of enabled SSL/TLS protocols.
   *
   * @param enabledSecureTransportProtocols  the SSL/TLS protocols to enable
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setEnabledSecureTransportProtocols(Set<String> enabledSecureTransportProtocols) {
    getOrCreateSSLOptions().setEnabledSecureTransportProtocols(enabledSecureTransportProtocols);
    return this;
  }

  /**
   * Add an enabled SSL/TLS protocols, appended to the ordered protocols.
   *
   * @param protocol  the SSL/TLS protocol to enable
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions addEnabledSecureTransportProtocol(String protocol) {
    getOrCreateSSLOptions().addEnabledSecureTransportProtocol(protocol);
    return this;
  }

  /**
   * Removes an enabled SSL/TLS protocol from the ordered protocols.
   *
   * @param protocol the SSL/TLS protocol to disable
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions removeEnabledSecureTransportProtocol(String protocol) {
    getOrCreateSSLOptions().removeEnabledSecureTransportProtocol(protocol);
    return this;
  }

  /**
   * @return wether {@code TCP_FASTOPEN} option is enabled
   */
  public boolean isTcpFastOpen() {
    return transportOptions.isTcpFastOpen();
  }

  /**
   * Enable the {@code TCP_FASTOPEN} option - only with linux native transport.
   *
   * @param tcpFastOpen the fast open value
   */
  public TCPSSLOptions setTcpFastOpen(boolean tcpFastOpen) {
    transportOptions.setTcpFastOpen(tcpFastOpen);
    return this;
  }

  /**
   * @return wether {@code TCP_CORK} option is enabled
   */
  public boolean isTcpCork() {
    return transportOptions.isTcpCork();
  }

  /**
   * Enable the {@code TCP_CORK} option - only with linux native transport.
   *
   * @param tcpCork the cork value
   */
  public TCPSSLOptions setTcpCork(boolean tcpCork) {
    transportOptions.setTcpCork(tcpCork);
    return this;
  }

  /**
   * @return wether {@code TCP_QUICKACK} option is enabled
   */
  public boolean isTcpQuickAck() {
    return transportOptions.isTcpQuickAck();
  }

  /**
   * Enable the {@code TCP_QUICKACK} option - only with linux native transport.
   *
   * @param tcpQuickAck the quick ack value
   */
  public TCPSSLOptions setTcpQuickAck(boolean tcpQuickAck) {
    transportOptions.setTcpQuickAck(tcpQuickAck);
    return this;
  }

  /**
   *
   * @return the {@code TCP_USER_TIMEOUT} value
   */
  public int getTcpUserTimeout() {
    return transportOptions.getTcpUserTimeout();
  }

  /**
   * Sets the {@code TCP_USER_TIMEOUT} option - only with linux native transport.
   *
   * @param tcpUserTimeout the tcp user timeout value
   */
  public TCPSSLOptions setTcpUserTimeout(int tcpUserTimeout) {
    transportOptions.setTcpUserTimeout(tcpUserTimeout);
    return this;
  }

  /**
   * Returns the enabled SSL/TLS protocols
   * @return the enabled protocols
   */
  public Set<String> getEnabledSecureTransportProtocols() {
    SSLOptions o = sslOptions;
    return o != null ? o.getEnabledSecureTransportProtocols() : new LinkedHashSet<>(SSLOptions.DEFAULT_ENABLED_SECURE_TRANSPORT_PROTOCOLS);
  }

  /**
   * @return the SSL handshake timeout, in time unit specified by {@link #getSslHandshakeTimeoutUnit()}.
   */
  public long getSslHandshakeTimeout() {
    SSLOptions o = sslOptions;
    return o != null ? o.getSslHandshakeTimeout() : SSLOptions.DEFAULT_SSL_HANDSHAKE_TIMEOUT;
  }

  /**
   * Set the SSL handshake timeout, default time unit is seconds.
   *
   * @param sslHandshakeTimeout the SSL handshake timeout to set, in milliseconds
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setSslHandshakeTimeout(long sslHandshakeTimeout) {
    getOrCreateSSLOptions().setSslHandshakeTimeout(sslHandshakeTimeout);
    return this;
  }

  /**
   * Set the SSL handshake timeout unit. If not specified, default is seconds.
   *
   * @param sslHandshakeTimeoutUnit specify time unit.
   * @return a reference to this, so the API can be used fluently
   */
  public TCPSSLOptions setSslHandshakeTimeoutUnit(TimeUnit sslHandshakeTimeoutUnit) {
    getOrCreateSSLOptions().setSslHandshakeTimeoutUnit(sslHandshakeTimeoutUnit);
    return this;
  }

  /**
   * @return the SSL handshake timeout unit.
   */
  public TimeUnit getSslHandshakeTimeoutUnit() {
    SSLOptions o = sslOptions;
    return o != null ? o.getSslHandshakeTimeoutUnit() : SSLOptions.DEFAULT_SSL_HANDSHAKE_TIMEOUT_TIME_UNIT;
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

}
