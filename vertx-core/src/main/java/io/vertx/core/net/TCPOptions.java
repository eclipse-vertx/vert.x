/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.core.net;

import io.vertx.codegen.annotations.Options;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.impl.SocketDefaults;

import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class TCPOptions extends NetworkOptions {

  // TCP stuff
  private static SocketDefaults SOCK_DEFAULTS = SocketDefaults.instance;

  private static final boolean DEFAULT_TCPNODELAY = true;
  private static final boolean DEFAULT_TCPKEEPALIVE = SOCK_DEFAULTS.isTcpKeepAlive();
  private static final int DEFAULT_SOLINGER = SOCK_DEFAULTS.getSoLinger();

  private boolean tcpNoDelay = DEFAULT_TCPNODELAY;
  private boolean tcpKeepAlive = DEFAULT_TCPKEEPALIVE;
  private int soLinger = DEFAULT_SOLINGER;
  private boolean usePooledBuffers;
  
  // SSL stuff

  private boolean ssl;
  private KeyStoreOptions keyStore;
  private TrustStoreOptions trustStore;
  private Set<String> enabledCipherSuites = new HashSet<>();

  public TCPOptions(TCPOptions other) {
    super(other);
    this.tcpNoDelay = other.tcpNoDelay;
    this.tcpKeepAlive = other.tcpKeepAlive;
    this.soLinger = other.soLinger;
    this.usePooledBuffers = other.usePooledBuffers;
    this.ssl = other.ssl;
    this.keyStore = other.keyStore != null ? other.keyStore.clone() : null;
    this.trustStore = other.trustStore != null ? other.trustStore.clone() : null;
    this.enabledCipherSuites = other.enabledCipherSuites == null ? null : new HashSet<>(other.enabledCipherSuites);
  }

  public TCPOptions(JsonObject json) {
    super(json);
    this.tcpNoDelay = json.getBoolean("tcpNoDelay", DEFAULT_TCPNODELAY);
    this.tcpKeepAlive = json.getBoolean("tcpKeepAlive", DEFAULT_TCPKEEPALIVE);
    this.soLinger = json.getInteger("soLinger", DEFAULT_SOLINGER);
    this.usePooledBuffers = json.getBoolean("usePooledBuffers", false);
    this.ssl = json.getBoolean("ssl", false);
    JsonObject keyStoreJson = json.getObject("keyStore");
    if (keyStoreJson != null) {
      String type = keyStoreJson.getString("type", null);
      String path = keyStoreJson.getString("path", null);
      String password = keyStoreJson.getString("password", null);
      String key = keyStoreJson.getString("key", null);
      String cert = keyStoreJson.getString("cert", null);
      switch (type != null ? type.toLowerCase() : "jks") {
        case "jks":
          keyStore = new JKSOptions().setPath(path).setPassword(password);
          break;
        case "pkcs12":
          keyStore = new PKCS12Options().setPath(path).setPassword(password);
          break;
        case "keycert":
          keyStore = new KeyCertOptions().setKeyPath(key).setCertPath(cert);
          break;
      }
    }
    JsonObject trustStoreJson = json.getObject("trustStore");
    if (trustStoreJson != null) {
      String type = trustStoreJson.getString("type", null);
      String path = trustStoreJson.getString("path", null);
      String password = trustStoreJson.getString("password", null);
      JsonArray caJson = trustStoreJson.getArray("ca", null);
      switch (type != null ? type.toLowerCase() : "jks") {
        case "jks":
          trustStore = new JKSOptions().setPath(path).setPassword(password);
          break;
        case "pkcs12":
          trustStore = new PKCS12Options().setPath(path).setPassword(password);
          break;
        case "ca":
          CaOptions ca = new CaOptions();
          if (caJson != null) {
            caJson.forEach(caElt -> ca.addCertPath(caElt.toString()));
          }
          trustStore = ca;
          break;
      }
    }
    JsonArray arr = json.getArray("enabledCipherSuites");
    this.enabledCipherSuites = arr == null ? null : new HashSet<String>(arr.toList());
  }

  public TCPOptions() {
    super();
    tcpNoDelay = DEFAULT_TCPNODELAY;
    tcpKeepAlive = DEFAULT_TCPKEEPALIVE;
    soLinger = DEFAULT_SOLINGER;
  }

  public boolean isTcpNoDelay() {
    return tcpNoDelay;
  }

  public TCPOptions setTcpNoDelay(boolean tcpNoDelay) {
    this.tcpNoDelay = tcpNoDelay;
    return this;
  }

  public boolean isTcpKeepAlive() {
    return tcpKeepAlive;
  }

  public TCPOptions setTcpKeepAlive(boolean tcpKeepAlive) {
    this.tcpKeepAlive = tcpKeepAlive;
    return this;
  }

  public int getSoLinger() {
    return soLinger;
  }

  public TCPOptions setSoLinger(int soLinger) {
    if (soLinger < 0) {
      throw new IllegalArgumentException("soLinger must be >= 0");
    }
    this.soLinger = soLinger;
    return this;
  }

  public boolean isUsePooledBuffers() {
    return usePooledBuffers;
  }

  public TCPOptions setUsePooledBuffers(boolean usePooledBuffers) {
    this.usePooledBuffers = usePooledBuffers;
    return this;
  }

  public boolean isSsl() {
    return ssl;
  }

  public TCPOptions setSsl(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  public KeyStoreOptions getKeyStore() {
    return keyStore;
  }

  public TCPOptions setKeyStore(KeyStoreOptions keyStore) {
    this.keyStore = keyStore;
    return this;
  }

  public TrustStoreOptions getTrustStore() {
    return trustStore;
  }

  public TCPOptions setTrustStore(TrustStoreOptions trustStore) {
    this.trustStore = trustStore;
    return this;
  }

  public TCPOptions addEnabledCipherSuite(String suite) {
    enabledCipherSuites.add(suite);
    return this;
  }

  public Set<String> getEnabledCipherSuites() {
    return enabledCipherSuites;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TCPOptions)) return false;
    if (!super.equals(o)) return false;

    TCPOptions that = (TCPOptions) o;

    if (soLinger != that.soLinger) return false;
    if (ssl != that.ssl) return false;
    if (tcpKeepAlive != that.tcpKeepAlive) return false;
    if (tcpNoDelay != that.tcpNoDelay) return false;
    if (usePooledBuffers != that.usePooledBuffers) return false;
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
    result = 31 * result + (ssl ? 1 : 0);
    result = 31 * result + (keyStore != null ? keyStore.hashCode() : 0);
    result = 31 * result + (trustStore != null ? trustStore.hashCode() : 0);
    result = 31 * result + (enabledCipherSuites != null ? enabledCipherSuites.hashCode() : 0);
    return result;
  }
}
