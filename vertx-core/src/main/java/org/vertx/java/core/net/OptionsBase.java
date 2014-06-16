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

package org.vertx.java.core.net;

import org.vertx.java.core.net.impl.SocketDefaults;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class OptionsBase {

  // TCP stuff
  private static SocketDefaults SOCK_DEFAULTS = SocketDefaults.instance;

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

  public OptionsBase(OptionsBase other) {
    this.sendBufferSize = other.sendBufferSize;
    this.receiveBufferSize = other.receiveBufferSize;
    this.reuseAddress = other.reuseAddress;
    this.trafficClass = other.trafficClass;
    this.tcpNoDelay = other.tcpNoDelay;
    this.tcpKeepAlive = other.tcpKeepAlive;
    this.soLinger = other.soLinger;
    this.usePooledBuffers = other.usePooledBuffers;
    this.ssl = other.ssl;
    this.keyStorePath = other.keyStorePath;
    this.keyStorePassword = other.keyStorePassword;
    this.trustStorePath = other.trustStorePath;
    this.trustStorePassword = other.trustStorePassword;
  }

  public OptionsBase() {
  }

  public int getSendBufferSize() {
    return sendBufferSize;
  }

  public OptionsBase setSendBufferSize(int sendBufferSize) {
    if (sendBufferSize < 1) {
      throw new IllegalArgumentException("sendBufferSize must be > 0");
    }
    this.sendBufferSize = sendBufferSize;
    return this;
  }

  public int getReceiveBufferSize() {
    return receiveBufferSize;
  }

  public OptionsBase setReceiveBufferSize(int receiveBufferSize) {
    if (receiveBufferSize < 1) {
      throw new IllegalArgumentException("receiveBufferSize must be > 0");
    }
    this.receiveBufferSize = receiveBufferSize;
    return this;
  }

  public boolean isReuseAddress() {
    return reuseAddress;
  }

  public OptionsBase setReuseAddress(boolean reuseAddress) {
    this.reuseAddress = reuseAddress;
    return this;
  }

  public int getTrafficClass() {
    return trafficClass;
  }

  public OptionsBase setTrafficClass(int trafficClass) {
    if (trafficClass < 0 || trafficClass > 255) {
      throw new IllegalArgumentException("trafficClass tc must be 0 <= tc <= 255");
    }
    this.trafficClass = trafficClass;
    return this;
  }

  public boolean isTcpNoDelay() {
    return tcpNoDelay;
  }

  public OptionsBase setTcpNoDelay(boolean tcpNoDelay) {
    this.tcpNoDelay = tcpNoDelay;
    return this;
  }

  public boolean isTcpKeepAlive() {
    return tcpKeepAlive;
  }

  public OptionsBase setTcpKeepAlive(boolean tcpKeepAlive) {
    this.tcpKeepAlive = tcpKeepAlive;
    return this;
  }

  public int getSoLinger() {
    return soLinger;
  }

  public OptionsBase setSoLinger(int soLinger) {
    if (soLinger < 0) {
      throw new IllegalArgumentException("soLinger must be >= 0");
    }
    this.soLinger = soLinger;
    return this;
  }

  public boolean isUsePooledBuffers() {
    return usePooledBuffers;
  }

  public OptionsBase setUsePooledBuffers(boolean usePooledBuffers) {
    this.usePooledBuffers = usePooledBuffers;
    return this;
  }

  public boolean isSsl() {
    return ssl;
  }

  public OptionsBase setSsl(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  public String getKeyStorePath() {
    return keyStorePath;
  }

  public OptionsBase setKeyStorePath(String keyStorePath) {
    this.keyStorePath = keyStorePath;
    return this;
  }

  public String getKeyStorePassword() {
    return keyStorePassword;
  }

  public OptionsBase setKeyStorePassword(String keyStorePassword) {
    this.keyStorePassword = keyStorePassword;
    return this;
  }

  public String getTrustStorePath() {
    return trustStorePath;
  }

  public OptionsBase setTrustStorePath(String trustStorePath) {
    this.trustStorePath = trustStorePath;
    return this;
  }

  public String getTrustStorePassword() {
    return trustStorePassword;
  }

  public OptionsBase setTrustStorePassword(String trustStorePassword) {
    this.trustStorePassword = trustStorePassword;
    return this;
  }
}
