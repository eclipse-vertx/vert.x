/*
 * Copyright 2014 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
 */

package io.vertx.core.net.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.CaOptions;
import io.vertx.core.net.JKSOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.KeyStoreOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.PKCS12Options;
import io.vertx.core.net.TrustStoreOptions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetClientOptionsImpl implements NetClientOptions {

  private static final int DEFAULT_SENDBUFFERSIZE = -1;
  private static final int DEFAULT_RECEIVEBUFFERSIZE = -1;
  private static final boolean DEFAULT_REUSEADDRESS = true;
  private static final int DEFAULT_TRAFFICCLASS = -1;

  private int sendBufferSize = DEFAULT_SENDBUFFERSIZE;
  private int receiveBufferSize = DEFAULT_RECEIVEBUFFERSIZE;
  private boolean reuseAddress = DEFAULT_REUSEADDRESS;
  private int trafficClass = DEFAULT_TRAFFICCLASS;

  // TCP stuff
  private static SocketDefaults SOCK_DEFAULTS = SocketDefaults.instance;

  private static final boolean DEFAULT_TCPNODELAY = true;
  private static final boolean DEFAULT_TCPKEEPALIVE = SOCK_DEFAULTS.isTcpKeepAlive();
  private static final int DEFAULT_SOLINGER = SOCK_DEFAULTS.getSoLinger();

  private boolean tcpNoDelay = DEFAULT_TCPNODELAY;
  private boolean tcpKeepAlive = DEFAULT_TCPKEEPALIVE;
  private int soLinger = DEFAULT_SOLINGER;
  private boolean usePooledBuffers;
  private int idleTimeout;

  // SSL stuff

  private boolean ssl;
  private KeyStoreOptions keyStore;
  private TrustStoreOptions trustStore;
  private Set<String> enabledCipherSuites = new HashSet<>();

  private static final int DEFAULT_CONNECTTIMEOUT = 60000;

  // Client specific TCP stuff

  private int connectTimeout;

  // Client specific SSL stuff

  private boolean trustAll;
  private ArrayList<String> crlPaths;
  private ArrayList<Buffer> crlValues;

  private static final long DEFAULT_RECONNECTINTERVAL = 1000;

  private int reconnectAttempts;
  private long reconnectInterval;

  NetClientOptionsImpl(NetClientOptions other) {
    this.sendBufferSize = other.getSendBufferSize();
    this.receiveBufferSize = other.getReceiveBufferSize();
    this.reuseAddress = other.isReuseAddress();
    this.trafficClass = other.getTrafficClass();
    this.tcpNoDelay = other.isTcpNoDelay();
    this.tcpKeepAlive = other.isTcpKeepAlive();
    this.soLinger = other.getSoLinger();
    this.usePooledBuffers = other.isUsePooledBuffers();
    this.ssl = other.isSsl();
    this.keyStore = other.getKeyStoreOptions() != null ? other.getKeyStoreOptions().clone() : null;
    this.trustStore = other.getTrustStoreOptions() != null ? other.getTrustStoreOptions().clone() : null;
    this.enabledCipherSuites = other.getEnabledCipherSuites() == null ? null : new HashSet<>(other.getEnabledCipherSuites());
    this.connectTimeout = other.getConnectTimeout();
    this.trustAll = other.isTrustAll();
    this.crlPaths = new ArrayList<>(other.getCrlPaths());
    this.crlValues = new ArrayList<>(other.getCrlValues());
    this.reconnectAttempts = other.getReconnectAttempts();
    this.reconnectInterval = other.getReconnectInterval();
    this.idleTimeout = other.getIdleTimeout();
  }

  NetClientOptionsImpl(JsonObject json) {
    this.sendBufferSize = json.getInteger("sendBufferSize", DEFAULT_SENDBUFFERSIZE);
    this.receiveBufferSize = json.getInteger("receiveBufferSize", DEFAULT_RECEIVEBUFFERSIZE);
    this.reuseAddress = json.getBoolean("reuseAddress", DEFAULT_REUSEADDRESS);
    this.trafficClass = json.getInteger("trafficClass", DEFAULT_TRAFFICCLASS);
    this.tcpNoDelay = json.getBoolean("tcpNoDelay", DEFAULT_TCPNODELAY);
    this.tcpKeepAlive = json.getBoolean("tcpKeepAlive", DEFAULT_TCPKEEPALIVE);
    this.soLinger = json.getInteger("soLinger", DEFAULT_SOLINGER);
    this.usePooledBuffers = json.getBoolean("usePooledBuffers", false);
    this.idleTimeout = json.getInteger("idleTimeout", 0);
    this.ssl = json.getBoolean("ssl", false);
    JsonObject keyStoreJson = json.getObject("keyStoreOptions");
    if (keyStoreJson != null) {
      String type = keyStoreJson.getString("type", null);
      switch (type != null ? type.toLowerCase() : "jks") {
        case "jks":
          keyStore = JKSOptions.optionsFromJson(keyStoreJson);
          break;
        case "pkcs12":
          keyStore = PKCS12Options.optionsFromJson(keyStoreJson);
          break;
        case "keycert":
          keyStore = KeyCertOptions.optionsFromJson(keyStoreJson);
          break;
        default:
          throw new IllegalArgumentException("Invalid key store type: " + type);
      }
    }
    JsonObject trustStoreJson = json.getObject("trustStoreOptions");
    if (trustStoreJson != null) {
      String type = trustStoreJson.getString("type", null);
      switch (type != null ? type.toLowerCase() : "jks") {
        case "jks":
          trustStore = JKSOptions.optionsFromJson(trustStoreJson);
          break;
        case "pkcs12":
          trustStore = PKCS12Options.optionsFromJson(trustStoreJson);
          break;
        case "ca":
          trustStore = CaOptions.optionsFromJson(trustStoreJson);
          break;
        default:
          throw new IllegalArgumentException("Invalid trust store type: " + type);
      }
    }
    JsonArray arr = json.getArray("enabledCipherSuites");
    this.enabledCipherSuites = arr == null ? null : new HashSet<String>(arr.toList());
    this.connectTimeout = json.getInteger("connectTimeout", DEFAULT_CONNECTTIMEOUT);
    this.trustAll = json.getBoolean("trustAll", false);
    arr = json.getArray("crlPaths");
    this.crlPaths = arr == null ? new ArrayList<>() : new ArrayList<String>(arr.toList());
    this.crlValues = new ArrayList<>();
    arr = json.getArray("crlValues");
    if (arr != null) {
      ((List<byte[]>) arr.toList()).stream().map(Buffer::buffer).forEach(crlValues::add);
    }
    this.reconnectAttempts = json.getInteger("reconnectAttempts", 0);
    this.reconnectInterval = json.getLong("reconnectInterval", DEFAULT_RECONNECTINTERVAL);
  }

  NetClientOptionsImpl() {
    sendBufferSize = DEFAULT_SENDBUFFERSIZE;
    receiveBufferSize = DEFAULT_RECEIVEBUFFERSIZE;
    reuseAddress = DEFAULT_REUSEADDRESS;
    trafficClass = DEFAULT_TRAFFICCLASS;
    tcpNoDelay = DEFAULT_TCPNODELAY;
    tcpKeepAlive = DEFAULT_TCPKEEPALIVE;
    soLinger = DEFAULT_SOLINGER;
    this.connectTimeout = DEFAULT_CONNECTTIMEOUT;
    this.crlPaths = new ArrayList<>();
    this.crlValues = new ArrayList<>();
    this.reconnectInterval = DEFAULT_RECONNECTINTERVAL;
  }

  @Override
  public int getSendBufferSize() {
    return sendBufferSize;
  }

  @Override
  public NetClientOptions setSendBufferSize(int sendBufferSize) {
    if (sendBufferSize < 1) {
      throw new IllegalArgumentException("sendBufferSize must be > 0");
    }
    this.sendBufferSize = sendBufferSize;
    return this;
  }

  @Override
  public int getReceiveBufferSize() {
    return receiveBufferSize;
  }

  @Override
  public NetClientOptions setReceiveBufferSize(int receiveBufferSize) {
    if (receiveBufferSize < 1) {
      throw new IllegalArgumentException("receiveBufferSize must be > 0");
    }
    this.receiveBufferSize = receiveBufferSize;
    return this;
  }

  @Override
  public boolean isReuseAddress() {
    return reuseAddress;
  }

  @Override
  public NetClientOptions setReuseAddress(boolean reuseAddress) {
    this.reuseAddress = reuseAddress;
    return this;
  }

  @Override
  public int getTrafficClass() {
    return trafficClass;
  }

  @Override
  public NetClientOptions setTrafficClass(int trafficClass) {
    if (trafficClass < 0 || trafficClass > 255) {
      throw new IllegalArgumentException("trafficClass tc must be 0 <= tc <= 255");
    }
    this.trafficClass = trafficClass;
    return this;
  }

  @Override
  public boolean isTcpNoDelay() {
    return tcpNoDelay;
  }

  @Override
  public NetClientOptions setTcpNoDelay(boolean tcpNoDelay) {
    this.tcpNoDelay = tcpNoDelay;
    return this;
  }

  @Override
  public boolean isTcpKeepAlive() {
    return tcpKeepAlive;
  }

  @Override
  public NetClientOptions setTcpKeepAlive(boolean tcpKeepAlive) {
    this.tcpKeepAlive = tcpKeepAlive;
    return this;
  }

  @Override
  public int getSoLinger() {
    return soLinger;
  }

  @Override
  public NetClientOptions setSoLinger(int soLinger) {
    if (soLinger < 0) {
      throw new IllegalArgumentException("soLinger must be >= 0");
    }
    this.soLinger = soLinger;
    return this;
  }

  @Override
  public boolean isUsePooledBuffers() {
    return usePooledBuffers;
  }

  @Override
  public NetClientOptions setUsePooledBuffers(boolean usePooledBuffers) {
    this.usePooledBuffers = usePooledBuffers;
    return this;
  }

  @Override
  public NetClientOptions setIdleTimeout(int idleTimeout) {
    if (idleTimeout < 0) {
      throw new IllegalArgumentException("idleTimeout must be >= 0");
    }
    this.idleTimeout = idleTimeout;
    return this;
  }

  @Override
  public int getIdleTimeout() {
    return idleTimeout;
  }

  @Override
  public boolean isSsl() {
    return ssl;
  }

  @Override
  public NetClientOptions setSsl(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  @Override
  public KeyStoreOptions getKeyStoreOptions() {
    return keyStore;
  }

  @Override
  public NetClientOptions setKeyStoreOptions(KeyStoreOptions keyStore) {
    this.keyStore = keyStore;
    return this;
  }

  @Override
  public TrustStoreOptions getTrustStoreOptions() {
    return trustStore;
  }

  @Override
  public NetClientOptions setTrustStoreOptions(TrustStoreOptions trustStore) {
    this.trustStore = trustStore;
    return this;
  }

  @Override
  public NetClientOptions addEnabledCipherSuite(String suite) {
    enabledCipherSuites.add(suite);
    return this;
  }

  @Override
  public Set<String> getEnabledCipherSuites() {
    return enabledCipherSuites;
  }

  @Override
  public boolean isTrustAll() {
    return trustAll;
  }

  @Override
  public NetClientOptions setTrustAll(boolean trustAll) {
    this.trustAll = trustAll;
    return this;
  }

  @Override
  public List<String> getCrlPaths() {
    return crlPaths;
  }

  @Override
  public NetClientOptions addCrlPath(String crlPath) throws NullPointerException {
    Objects.requireNonNull(crlPath, "No null crl accepted");
    crlPaths.add(crlPath);
    return this;
  }

  @Override
  public List<Buffer> getCrlValues() {
    return crlValues;
  }

  @Override
  public NetClientOptions addCrlValue(Buffer crlValue) throws NullPointerException {
    Objects.requireNonNull(crlValue, "No null crl accepted");
    crlValues.add(crlValue);
    return this;
  }

  @Override
  public int getConnectTimeout() {
    return connectTimeout;
  }

  @Override
  public NetClientOptions setConnectTimeout(int connectTimeout) {
    if (connectTimeout < 0) {
      throw new IllegalArgumentException("connectTimeout must be >= 0");
    }
    this.connectTimeout = connectTimeout;
    return this;
  }

  @Override
  public NetClientOptions setReconnectAttempts(int attempts) {
    if (attempts < -1) {
      throw new IllegalArgumentException("reconnect attempts must be >= -1");
    }
    this.reconnectAttempts = attempts;
    return this;
  }

  @Override
  public int getReconnectAttempts() {
    return reconnectAttempts;
  }

  @Override
  public NetClientOptions setReconnectInterval(long interval) {
    if (interval < 1) {
      throw new IllegalArgumentException("reconnect interval nust be >= 1");
    }
    this.reconnectInterval = interval;
    return this;
  }

  @Override
  public long getReconnectInterval() {
    return reconnectInterval;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    NetClientOptionsImpl that = (NetClientOptionsImpl) o;

    if (connectTimeout != that.connectTimeout) return false;
    if (idleTimeout != that.idleTimeout) return false;
    if (receiveBufferSize != that.receiveBufferSize) return false;
    if (reconnectAttempts != that.reconnectAttempts) return false;
    if (reconnectInterval != that.reconnectInterval) return false;
    if (reuseAddress != that.reuseAddress) return false;
    if (sendBufferSize != that.sendBufferSize) return false;
    if (soLinger != that.soLinger) return false;
    if (ssl != that.ssl) return false;
    if (tcpKeepAlive != that.tcpKeepAlive) return false;
    if (tcpNoDelay != that.tcpNoDelay) return false;
    if (trafficClass != that.trafficClass) return false;
    if (trustAll != that.trustAll) return false;
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
    int result = sendBufferSize;
    result = 31 * result + receiveBufferSize;
    result = 31 * result + (reuseAddress ? 1 : 0);
    result = 31 * result + trafficClass;
    result = 31 * result + (tcpNoDelay ? 1 : 0);
    result = 31 * result + (tcpKeepAlive ? 1 : 0);
    result = 31 * result + soLinger;
    result = 31 * result + (usePooledBuffers ? 1 : 0);
    result = 31 * result + idleTimeout;
    result = 31 * result + (ssl ? 1 : 0);
    result = 31 * result + (keyStore != null ? keyStore.hashCode() : 0);
    result = 31 * result + (trustStore != null ? trustStore.hashCode() : 0);
    result = 31 * result + (enabledCipherSuites != null ? enabledCipherSuites.hashCode() : 0);
    result = 31 * result + connectTimeout;
    result = 31 * result + (trustAll ? 1 : 0);
    result = 31 * result + (crlPaths != null ? crlPaths.hashCode() : 0);
    result = 31 * result + (crlValues != null ? crlValues.hashCode() : 0);
    result = 31 * result + reconnectAttempts;
    result = 31 * result + (int) (reconnectInterval ^ (reconnectInterval >>> 32));
    return result;
  }
}
