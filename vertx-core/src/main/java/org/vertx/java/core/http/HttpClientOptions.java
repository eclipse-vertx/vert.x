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

import org.vertx.java.core.net.ClientOptionsBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientOptions extends ClientOptionsBase {

  // Client specific SSL stuff

  private boolean verifyHost = true;

  // HTTP stuff

  private int maxPoolSize = 5;
  private boolean keepAlive = true;
  private boolean pipelining;
  private boolean tryUseCompression;

  public HttpClientOptions() {
  }

  public HttpClientOptions(HttpClientOptions other) {
    super(other);
    this.verifyHost = other.verifyHost;
    this.maxPoolSize = other.maxPoolSize;
    this.keepAlive = other.keepAlive;
    this.pipelining = other.pipelining;
    this.tryUseCompression = other.tryUseCompression;
  }

  public int getMaxPoolSize() {
    return maxPoolSize;
  }

  public HttpClientOptions setMaxPoolSize(int maxPoolSize) {
    if (maxPoolSize < 1) {
      throw new IllegalArgumentException("maxPoolSize must be > 0");
    }
    this.maxPoolSize = maxPoolSize;
    return this;
  }

  public boolean isKeepAlive() {
    return keepAlive;
  }

  public HttpClientOptions setKeepAlive(boolean keepAlive) {
    this.keepAlive = keepAlive;
    return this;
  }

  public boolean isPipelining() {
    return pipelining;
  }

  public HttpClientOptions setPipelining(boolean pipelining) {
    this.pipelining = pipelining;
    return this;
  }

  public boolean isVerifyHost() {
    return verifyHost;
  }

  public HttpClientOptions setVerifyHost(boolean verifyHost) {
    this.verifyHost = verifyHost;
    return this;
  }

  public boolean isTryUseCompression() {
    return tryUseCompression;
  }

  public HttpClientOptions setTryUseCompression(boolean tryUseCompression) {
    this.tryUseCompression = tryUseCompression;
    return this;
  }

  // Override common implementation

  @Override
  public int getConnectTimeout() {
    return super.getConnectTimeout();
  }

  @Override
  public HttpClientOptions setConnectTimeout(int connectTimeout) {
    super.setConnectTimeout(connectTimeout);
    return this;
  }

  public boolean isTrustAll() {
    return super.isTrustAll();
  }

  public HttpClientOptions setTrustAll(boolean trustAll) {
    super.setTrustAll(trustAll);
    return this;
  }

  @Override
  public HttpClientOptions setSendBufferSize(int sendBufferSize) {
    super.setSendBufferSize(sendBufferSize);
    return this;
  }

  @Override
  public HttpClientOptions setReceiveBufferSize(int receiveBufferSize) {
    super.setReceiveBufferSize(receiveBufferSize);
    return this;
  }

  @Override
  public HttpClientOptions setReuseAddress(boolean reuseAddress) {
    super.setReuseAddress(reuseAddress);
    return this;
  }

  @Override
  public HttpClientOptions setTrafficClass(int trafficClass) {
    super.setTrafficClass(trafficClass);
    return this;
  }

  @Override
  public HttpClientOptions setTcpNoDelay(boolean tcpNoDelay) {
    super.setTcpNoDelay(tcpNoDelay);
    return this;
  }

  @Override
  public HttpClientOptions setTcpKeepAlive(boolean tcpKeepAlive) {
    super.setTcpKeepAlive(tcpKeepAlive);
    return this;
  }

  @Override
  public HttpClientOptions setSoLinger(int soLinger) {
    super.setSoLinger(soLinger);
    return this;
  }

  @Override
  public HttpClientOptions setUsePooledBuffers(boolean usePooledBuffers) {
    super.setUsePooledBuffers(usePooledBuffers);
    return this;
  }

  @Override
  public HttpClientOptions setSsl(boolean ssl) {
    super.setSsl(ssl);
    return this;
  }

  @Override
  public HttpClientOptions setKeyStorePath(String keyStorePath) {
    super.setKeyStorePath(keyStorePath);
    return this;
  }

  @Override
  public HttpClientOptions setKeyStorePassword(String keyStorePassword) {
    super.setKeyStorePassword(keyStorePassword);
    return this;
  }

  @Override
  public HttpClientOptions setTrustStorePath(String trustStorePath) {
    super.setTrustStorePath(trustStorePath);
    return this;
  }

  @Override
  public HttpClientOptions setTrustStorePassword(String trustStorePassword) {
    super.setTrustStorePassword(trustStorePassword);
    return this;
  }

}
