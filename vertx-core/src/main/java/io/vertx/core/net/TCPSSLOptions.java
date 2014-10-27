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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.impl.SocketDefaults;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class TCPSSLOptions extends NetworkOptions {

  public static final boolean DEFAULT_TCP_NO_DELAY = true;
  public static final boolean DEFAULT_TCP_KEEP_ALIVE = SocketDefaults.instance.isTcpKeepAlive();
  public static final int DEFAULT_SO_LINGER = SocketDefaults.instance.getSoLinger();
  public static final boolean DEFAULT_USE_POOLED_BUFFERS = false;
  public static final boolean DEFAULT_SSL = false;
  public static final int DEFAULT_IDLE_TIMEOUT = 0;  // TODO - shouldn't this be -1 ??

  private boolean tcpNoDelay;
  private boolean tcpKeepAlive;
  private int soLinger;
  private boolean usePooledBuffers;
  private int idleTimeout;
  private boolean ssl;
  private KeyStoreOptions keyStore;
  private TrustStoreOptions trustStore;
  private Set<String> enabledCipherSuites = new HashSet<>();
  private ArrayList<String> crlPaths;
  private ArrayList<Buffer> crlValues;

  public TCPSSLOptions(TCPSSLOptions other) {
    super(other);
    this.tcpNoDelay = other.isTcpNoDelay();
    this.tcpKeepAlive = other.isTcpKeepAlive();
    this.soLinger = other.getSoLinger();
    this.usePooledBuffers = other.isUsePooledBuffers();
    this.idleTimeout = other.getIdleTimeout();
    this.ssl = other.isSsl();
    this.keyStore = other.getKeyStoreOptions() != null ? other.getKeyStoreOptions().clone() : null;
    this.trustStore = other.getTrustStoreOptions() != null ? other.getTrustStoreOptions().clone() : null;
    this.enabledCipherSuites = other.getEnabledCipherSuites() == null ? null : new HashSet<>(other.getEnabledCipherSuites());
    this.crlPaths = new ArrayList<>(other.getCrlPaths());
    this.crlValues = new ArrayList<>(other.getCrlValues());
  }

  public TCPSSLOptions(JsonObject json) {
    super(json);
    this.tcpNoDelay = json.getBoolean("tcpNoDelay", DEFAULT_TCP_NO_DELAY);
    this.tcpKeepAlive = json.getBoolean("tcpKeepAlive", DEFAULT_TCP_KEEP_ALIVE);
    this.soLinger = json.getInteger("soLinger", DEFAULT_SO_LINGER);
    this.usePooledBuffers = json.getBoolean("usePooledBuffers", false);
    this.idleTimeout = json.getInteger("idleTimeout", 0);
    this.ssl = json.getBoolean("ssl", false);
    JsonObject keyStoreJson = json.getJsonObject("keyStoreOptions");
    if (keyStoreJson != null) {
      String type = keyStoreJson.getString("type", null);
      switch (type != null ? type.toLowerCase() : "jks") {
        case "jks":
          keyStore = new JKSOptions(keyStoreJson);
          break;
        case "pkcs12":
          keyStore = new PKCS12Options(keyStoreJson);
          break;
        case "keycert":
          keyStore = new KeyCertOptions(keyStoreJson);
          break;
        default:
          throw new IllegalArgumentException("Invalid key store type: " + type);
      }
    }
    JsonObject trustStoreJson = json.getJsonObject("trustStoreOptions");
    if (trustStoreJson != null) {
      String type = trustStoreJson.getString("type", null);
      switch (type != null ? type.toLowerCase() : "jks") {
        case "jks":
          trustStore = new JKSOptions(trustStoreJson);
          break;
        case "pkcs12":
          trustStore = new PKCS12Options(trustStoreJson);
          break;
        case "ca":
          trustStore = new CaOptions(trustStoreJson);
          break;
        default:
          throw new IllegalArgumentException("Invalid trust store type: " + type);
      }
    }
    JsonArray arr = json.getJsonArray("enabledCipherSuites");
    this.enabledCipherSuites = arr == null ? null : new HashSet<String>(arr.getList());
    arr = json.getJsonArray("crlPaths");
    this.crlPaths = arr == null ? new ArrayList<>() : new ArrayList<String>(arr.getList());
    this.crlValues = new ArrayList<>();
    arr = json.getJsonArray("crlValues");
    if (arr != null) {
      ((List<byte[]>) arr.getList()).stream().map(Buffer::buffer).forEach(crlValues::add);
    }
  }

  public TCPSSLOptions() {
    super();
    tcpNoDelay = DEFAULT_TCP_NO_DELAY;
    tcpKeepAlive = DEFAULT_TCP_KEEP_ALIVE;
    soLinger = DEFAULT_SO_LINGER;
    usePooledBuffers = DEFAULT_USE_POOLED_BUFFERS;
    idleTimeout = DEFAULT_IDLE_TIMEOUT;
    ssl = DEFAULT_SSL;
    crlPaths = new ArrayList<>();
    crlValues = new ArrayList<>();
  }

  public boolean isTcpNoDelay() {
    return tcpNoDelay;
  }

  public TCPSSLOptions setTcpNoDelay(boolean tcpNoDelay) {
    this.tcpNoDelay = tcpNoDelay;
    return this;
  }

  public boolean isTcpKeepAlive() {
    return tcpKeepAlive;
  }

  public TCPSSLOptions setTcpKeepAlive(boolean tcpKeepAlive) {
    this.tcpKeepAlive = tcpKeepAlive;
    return this;
  }

  public int getSoLinger() {
    return soLinger;
  }

  public TCPSSLOptions setSoLinger(int soLinger) {
    if (soLinger < 0) {
      throw new IllegalArgumentException("soLinger must be >= 0");
    }
    this.soLinger = soLinger;
    return this;
  }

  public boolean isUsePooledBuffers() {
    return usePooledBuffers;
  }

  public TCPSSLOptions setUsePooledBuffers(boolean usePooledBuffers) {
    this.usePooledBuffers = usePooledBuffers;
    return this;
  }

  public TCPSSLOptions setIdleTimeout(int idleTimeout) {
    if (idleTimeout < 0) {
      throw new IllegalArgumentException("idleTimeout must be >= 0");
    }
    this.idleTimeout = idleTimeout;
    return this;
  }

  public int getIdleTimeout() {
    return idleTimeout;
  }

  public boolean isSsl() {
    return ssl;
  }

  public TCPSSLOptions setSsl(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  public KeyStoreOptions getKeyStoreOptions() {
    return keyStore;
  }

  public TCPSSLOptions setKeyStoreOptions(KeyStoreOptions keyStore) {
    this.keyStore = keyStore;
    return this;
  }

  public TrustStoreOptions getTrustStoreOptions() {
    return trustStore;
  }

  public TCPSSLOptions setTrustStoreOptions(TrustStoreOptions trustStore) {
    this.trustStore = trustStore;
    return this;
  }

  public TCPSSLOptions addEnabledCipherSuite(String suite) {
    enabledCipherSuites.add(suite);
    return this;
  }

  public Set<String> getEnabledCipherSuites() {
    return enabledCipherSuites;
  }

  public List<String> getCrlPaths() {
    return crlPaths;
  }

  public TCPSSLOptions addCrlPath(String crlPath) throws NullPointerException {
    Objects.requireNonNull(crlPath, "No null crl accepted");
    crlPaths.add(crlPath);
    return this;
  }

  public List<Buffer> getCrlValues() {
    return crlValues;
  }

  public TCPSSLOptions addCrlValue(Buffer crlValue) throws NullPointerException {
    Objects.requireNonNull(crlValue, "No null crl accepted");
    crlValues.add(crlValue);
    return this;
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
    if (keyStore != null ? !keyStore.equals(that.keyStore) : that.keyStore != null) return false;
    if (trustStore != null ? !trustStore.equals(that.trustStore) : that.trustStore != null) return false;

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
    result = 31 * result + (keyStore != null ? keyStore.hashCode() : 0);
    result = 31 * result + (trustStore != null ? trustStore.hashCode() : 0);
    result = 31 * result + (enabledCipherSuites != null ? enabledCipherSuites.hashCode() : 0);
    result = 31 * result + (crlPaths != null ? crlPaths.hashCode() : 0);
    result = 31 * result + (crlValues != null ? crlValues.hashCode() : 0);
    return result;
  }
}
