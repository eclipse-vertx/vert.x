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

package org.vertx.java.core.http;

import org.vertx.java.core.net.impl.SocketDefaults;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClientOptions {

  private static SocketDefaults SOCK_DEFAULTS = SocketDefaults.instance;

  // TCP stuff

  private int sendBufferSize = -1;
  private int receiveBufferSize = -1;
  private boolean reuseAddress = true;
  private int trafficClass = -1;

  private boolean tcpNoDelay = true;
  private boolean tcpKeepAlive = SOCK_DEFAULTS.isTcpKeepAlive();
  private int soLinger = SOCK_DEFAULTS.getSoLinger();
  private boolean usePooledBuffers;

  // SSL stuff

  private boolean ssl;
  private String keyStorePath;
  private String keyStorePassword;
  private String trustStorePath;
  private String trustStorePassword;
  private boolean trustAll;
  private boolean verifyHost = true;

  // Other stuff

  private int maxPoolSize = 5;
  private boolean keepAlive = true;
  private boolean pipelining;
  private int connectTimeout = 60000;
  private boolean tryUseCompression;

  public int getMaxPoolSize() {
    return maxPoolSize;
  }

  public ClientOptions setMaxPoolSize(int maxPoolSize) {
    if (maxPoolSize < 1) {
      throw new IllegalArgumentException("maxPoolSize must be > 0");
    }
    this.maxPoolSize = maxPoolSize;
    return this;
  }

  public boolean isKeepAlive() {
    return keepAlive;
  }

  public ClientOptions setKeepAlive(boolean keepAlive) {
    this.keepAlive = keepAlive;
    return this;
  }

  public boolean isPipelining() {
    return pipelining;
  }

  public ClientOptions setPipelining(boolean pipelining) {
    this.pipelining = pipelining;
    return this;
  }

  public int getSendBufferSize() {
    return sendBufferSize;
  }

  public ClientOptions setSendBufferSize(int sendBufferSize) {
    if (sendBufferSize < 1) {
      throw new IllegalArgumentException("sendBufferSize must be > 0");
    }
    this.sendBufferSize = sendBufferSize;
    return this;
  }

  public int getReceiveBufferSize() {
    return receiveBufferSize;
  }

  public ClientOptions setReceiveBufferSize(int receiveBufferSize) {
    if (receiveBufferSize < 1) {
      throw new IllegalArgumentException("receiveBufferSize must be > 0");
    }
    this.receiveBufferSize = receiveBufferSize;
    return this;
  }

  public boolean isReuseAddress() {
    return reuseAddress;
  }

  public ClientOptions setReuseAddress(boolean reuseAddress) {
    this.reuseAddress = reuseAddress;
    return this;
  }

  public int getTrafficClass() {
    return trafficClass;
  }

  public ClientOptions setTrafficClass(int trafficClass) {
    if (trafficClass < 0 || trafficClass > 255) {
      throw new IllegalArgumentException("trafficClass tc must be 0 <= tc <= 255");
    }
    this.trafficClass = trafficClass;
    return this;
  }

  public boolean isTcpNoDelay() {
    return tcpNoDelay;
  }

  public ClientOptions setTcpNoDelay(boolean tcpNoDelay) {
    this.tcpNoDelay = tcpNoDelay;
    return this;
  }

  public boolean isTcpKeepAlive() {
    return tcpKeepAlive;
  }

  public ClientOptions setTcpKeepAlive(boolean tcpKeepAlive) {
    this.tcpKeepAlive = tcpKeepAlive;
    return this;
  }

  public int getSoLinger() {
    return soLinger;
  }

  public ClientOptions setSoLinger(int soLinger) {
    if (soLinger < 0) {
      throw new IllegalArgumentException("soLinger must be >= 0");
    }
    this.soLinger = soLinger;
    return this;
  }

  public boolean isUsePooledBuffers() {
    return usePooledBuffers;
  }

  public ClientOptions setUsePooledBuffers(boolean usePooledBuffers) {
    this.usePooledBuffers = usePooledBuffers;
    return this;
  }

  public boolean isSsl() {
    return ssl;
  }

  public ClientOptions setSsl(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  public String getKeyStorePath() {
    return keyStorePath;
  }

  public ClientOptions setKeyStorePath(String keyStorePath) {
    this.keyStorePath = keyStorePath;
    return this;
  }

  public String getKeyStorePassword() {
    return keyStorePassword;
  }

  public ClientOptions setKeyStorePassword(String keyStorePassword) {
    this.keyStorePassword = keyStorePassword;
    return this;
  }

  public String getTrustStorePath() {
    return trustStorePath;
  }

  public ClientOptions setTrustStorePath(String trustStorePath) {
    this.trustStorePath = trustStorePath;
    return this;
  }

  public String getTrustStorePassword() {
    return trustStorePassword;
  }

  public ClientOptions setTrustStorePassword(String trustStorePassword) {
    this.trustStorePassword = trustStorePassword;
    return this;
  }

  public boolean isTrustAll() {
    return trustAll;
  }

  public ClientOptions setTrustAll(boolean trustAll) {
    this.trustAll = trustAll;
    return this;
  }

  public boolean isVerifyHost() {
    return verifyHost;
  }

  public ClientOptions setVerifyHost(boolean verifyHost) {
    this.verifyHost = verifyHost;
    return this;
  }

  public int getConnectTimeout() {
    return connectTimeout;
  }

  public ClientOptions setConnectTimeout(int connectTimeout) {
    if (connectTimeout < 0) {
      throw new IllegalArgumentException("connectTimeout must be >= 0");
    }
    this.connectTimeout = connectTimeout;
    return this;
  }

  public boolean isTryUseCompression() {
    return tryUseCompression;
  }

  public ClientOptions setTryUseCompression(boolean tryUseCompression) {
    this.tryUseCompression = tryUseCompression;
    return this;
  }

}
