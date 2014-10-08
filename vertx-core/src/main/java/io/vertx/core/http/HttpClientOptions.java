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

package io.vertx.core.http;

import io.vertx.codegen.annotations.Options;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.ClientOptionsBase;
import io.vertx.core.net.KeyStoreOptions;
import io.vertx.core.net.TrustStoreOptions;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class HttpClientOptions extends ClientOptionsBase {

  public static final int DEFAULT_MAX_POOL_SIZE = 5;
  public static final boolean DEFAULT_KEEP_ALIVE = true;
  public static final boolean DEFAULT_PIPELINING = false;
  public static final boolean DEFAULT_TRY_USE_COMPRESSION = false;
  public static final boolean DEFAULT_VERIFY_HOST = true;
  public static final int DEFAULT_MAX_WEBSOCKET_FRAME_SIZE = 65536;

  private boolean verifyHost = true;
  private int maxPoolSize;
  private boolean keepAlive;
  private boolean pipelining;
  private boolean tryUseCompression;
  private int maxWebsocketFrameSize;

  public HttpClientOptions(HttpClientOptions other) {
    super(other);
    this.verifyHost = other.isVerifyHost();
    this.maxPoolSize = other.getMaxPoolSize();
    this.keepAlive = other.isKeepAlive();
    this.pipelining = other.isPipelining();
    this.tryUseCompression = other.isTryUseCompression();
    this.maxWebsocketFrameSize = other.maxWebsocketFrameSize;
  }

  public HttpClientOptions(JsonObject json) {
    super(json);
    this.verifyHost = json.getBoolean("verifyHost", DEFAULT_VERIFY_HOST);
    this.maxPoolSize = json.getInteger("maxPoolSize", DEFAULT_MAX_POOL_SIZE);
    this.keepAlive = json.getBoolean("keepAlive", DEFAULT_KEEP_ALIVE);
    this.pipelining = json.getBoolean("pipelining", DEFAULT_PIPELINING);
    this.tryUseCompression = json.getBoolean("tryUseCompression", DEFAULT_TRY_USE_COMPRESSION);
    this.maxWebsocketFrameSize = json.getInteger("maxWebsocketFrameSize", DEFAULT_MAX_WEBSOCKET_FRAME_SIZE);
  }

  public HttpClientOptions() {
    super();
    verifyHost = DEFAULT_VERIFY_HOST;
    maxPoolSize = DEFAULT_MAX_POOL_SIZE;
    keepAlive = DEFAULT_KEEP_ALIVE;
    pipelining = DEFAULT_PIPELINING;
    tryUseCompression = DEFAULT_TRY_USE_COMPRESSION;
    maxWebsocketFrameSize = DEFAULT_MAX_WEBSOCKET_FRAME_SIZE;
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
  public HttpClientOptions setIdleTimeout(int idleTimeout) {
    super.setIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public HttpClientOptions setSsl(boolean ssl) {
    super.setSsl(ssl);
    return this;
  }

  @Override
  public HttpClientOptions setKeyStoreOptions(KeyStoreOptions keyStore) {
    super.setKeyStoreOptions(keyStore);
    return this;
  }

  @Override
  public HttpClientOptions setTrustStoreOptions(TrustStoreOptions trustStore) {
    super.setTrustStoreOptions(trustStore);
    return this;
  }

  @Override
  public HttpClientOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
    return this;
  }

  @Override
  public HttpClientOptions setConnectTimeout(int connectTimeout) {
    super.setConnectTimeout(connectTimeout);
    return this;
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

  @Override
  public HttpClientOptions setTrustAll(boolean trustAll) {
    super.setTrustAll(trustAll);
    return this;
  }

  public boolean isTryUseCompression() {
    return tryUseCompression;
  }

  public HttpClientOptions setTryUseCompression(boolean tryUseCompression) {
    this.tryUseCompression = tryUseCompression;
    return this;
  }

  public int getMaxWebsocketFrameSize() {
    return maxWebsocketFrameSize;
  }

  public HttpClientOptions setMaxWebsocketFrameSize(int maxWebsocketFrameSize) {
    this.maxWebsocketFrameSize = maxWebsocketFrameSize;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof HttpClientOptions)) return false;
    if (!super.equals(o)) return false;

    HttpClientOptions that = (HttpClientOptions) o;

    if (keepAlive != that.keepAlive) return false;
    if (maxPoolSize != that.maxPoolSize) return false;
    if (pipelining != that.pipelining) return false;
    if (tryUseCompression != that.tryUseCompression) return false;
    if (verifyHost != that.verifyHost) return false;
    if (maxWebsocketFrameSize != that.maxWebsocketFrameSize) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (verifyHost ? 1 : 0);
    result = 31 * result + maxPoolSize;
    result = 31 * result + (keepAlive ? 1 : 0);
    result = 31 * result + (pipelining ? 1 : 0);
    result = 31 * result + (tryUseCompression ? 1 : 0);
    result = 31 * result + maxWebsocketFrameSize;
    return result;
  }


}
