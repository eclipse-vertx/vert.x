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

package io.vertx.core.http;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.gen.Options;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.ClientOptions;
import io.vertx.core.net.KeyStoreOptions;
import io.vertx.core.net.TrustStoreOptions;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class HttpClientOptions extends ClientOptions {

  private static final int DEFAULT_MAXPOOLSIZE = 5;
  private static final boolean DEFAULT_KEEPALIVE = true;

  // Client specific SSL stuff

  private boolean verifyHost = true;

  // HTTP stuff

  private int maxPoolSize;
  private boolean keepAlive;
  private boolean pipelining;
  private boolean tryUseCompression;

  public HttpClientOptions() {
    super();
    this.maxPoolSize = DEFAULT_MAXPOOLSIZE;
    this.keepAlive = DEFAULT_KEEPALIVE;
  }

  public HttpClientOptions(HttpClientOptions other) {
    super(other);
    this.verifyHost = other.verifyHost;
    this.maxPoolSize = other.maxPoolSize;
    this.keepAlive = other.keepAlive;
    this.pipelining = other.pipelining;
    this.tryUseCompression = other.tryUseCompression;
  }

  public HttpClientOptions(JsonObject json) {
    super(json);
    this.maxPoolSize = json.getInteger("maxPoolSize", DEFAULT_MAXPOOLSIZE);
    this.keepAlive = json.getBoolean("keepAlive", DEFAULT_KEEPALIVE);
    this.pipelining = json.getBoolean("pipelining", false);
    this.tryUseCompression = json.getBoolean("tryUseCompression", false);
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
  public HttpClientOptions addCrlPath(String crlPath) throws NullPointerException {
    super.addCrlPath(crlPath);
    return this;
  }

  @Override
  public HttpClientOptions addCrlValue(Buffer crlValue) throws NullPointerException {
    super.addCrlValue(crlValue);
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
  public HttpClientOptions setKeyStore(KeyStoreOptions keyStore) {
    super.setKeyStore(keyStore);
    return this;
  }

  @Override
  public HttpClientOptions setTrustStore(TrustStoreOptions trustStore) {
    super.setTrustStore(trustStore);
    return this;
  }

  @Override
  public HttpClientOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
    return this;
  }


}
