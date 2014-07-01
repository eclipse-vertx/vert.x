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

import io.vertx.core.gen.VertxGen;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.impl.SocketDefaults;

import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
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
  private String keyStorePath;
  private String keyStorePassword;
  private String trustStorePath;
  private String trustStorePassword;
  private Set<String> enabledCipherSuites;

  public TCPOptions(TCPOptions other) {
    super(other);
    this.tcpNoDelay = other.tcpNoDelay;
    this.tcpKeepAlive = other.tcpKeepAlive;
    this.soLinger = other.soLinger;
    this.usePooledBuffers = other.usePooledBuffers;
    this.ssl = other.ssl;
    this.keyStorePath = other.keyStorePath;
    this.keyStorePassword = other.keyStorePassword;
    this.trustStorePath = other.trustStorePath;
    this.trustStorePassword = other.trustStorePassword;
    this.enabledCipherSuites = other.enabledCipherSuites == null ? null : new HashSet<>(other.enabledCipherSuites);
  }

  public TCPOptions(JsonObject json) {
    super(json);
    this.tcpNoDelay = json.getBoolean("tcpNoDelay", DEFAULT_TCPNODELAY);
    this.tcpKeepAlive = json.getBoolean("tcpKeepAlive", DEFAULT_TCPKEEPALIVE);
    this.soLinger = json.getInteger("soLinger", DEFAULT_SOLINGER);
    this.usePooledBuffers = json.getBoolean("usePooledBuffers", false);
    this.ssl = json.getBoolean("ssl", false);
    this.keyStorePath = json.getString("keyStorePath", null);
    this.keyStorePassword = json.getString("keyStorePassword", null);
    this.trustStorePath = json.getString("trustStorePath", null);
    this.trustStorePassword = json.getString("trustStorePassword", null);
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

  public String getKeyStorePath() {
    return keyStorePath;
  }

  public TCPOptions setKeyStorePath(String keyStorePath) {
    this.keyStorePath = keyStorePath;
    return this;
  }

  public String getKeyStorePassword() {
    return keyStorePassword;
  }

  public TCPOptions setKeyStorePassword(String keyStorePassword) {
    this.keyStorePassword = keyStorePassword;
    return this;
  }

  public String getTrustStorePath() {
    return trustStorePath;
  }

  public TCPOptions setTrustStorePath(String trustStorePath) {
    this.trustStorePath = trustStorePath;
    return this;
  }

  public String getTrustStorePassword() {
    return trustStorePassword;
  }

  public TCPOptions setTrustStorePassword(String trustStorePassword) {
    this.trustStorePassword = trustStorePassword;
    return this;
  }

  public TCPOptions addEnabledCipherSuite(String suite) {
    if (enabledCipherSuites == null) {
      enabledCipherSuites = new HashSet<>();
    }
    enabledCipherSuites.add(suite);
    return this;
  }

  public Set<String> getEnabledCipherSuites() {
    return enabledCipherSuites;
  }

}
