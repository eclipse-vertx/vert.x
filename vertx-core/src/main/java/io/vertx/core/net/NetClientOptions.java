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

import io.vertx.codegen.annotations.Options;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class NetClientOptions extends ClientOptionsBase {

  public static final int DEFAULT_RECONNECT_ATTEMPTS = 0;
  public static final long DEFAULT_RECONNECT_INTERVAL = 1000;

  private int reconnectAttempts;
  private long reconnectInterval;

  public NetClientOptions(NetClientOptions other) {
    super(other);
    this.reconnectAttempts = other.getReconnectAttempts();
    this.reconnectInterval = other.getReconnectInterval();
  }

  public NetClientOptions(JsonObject json) {
    super(json);
    this.reconnectAttempts = json.getInteger("reconnectAttempts", DEFAULT_RECONNECT_ATTEMPTS);
    this.reconnectInterval = json.getLong("reconnectInterval", DEFAULT_RECONNECT_INTERVAL);
  }

  public NetClientOptions() {
    super();
    this.reconnectAttempts = DEFAULT_RECONNECT_ATTEMPTS;
    this.reconnectInterval = DEFAULT_RECONNECT_INTERVAL;
  }

  @Override
  public NetClientOptions setSendBufferSize(int sendBufferSize) {
    super.setSendBufferSize(sendBufferSize);
    return this;
  }

  @Override
  public NetClientOptions setReceiveBufferSize(int receiveBufferSize) {
    super.setReceiveBufferSize(receiveBufferSize);
    return this;
  }

  @Override
  public NetClientOptions setReuseAddress(boolean reuseAddress) {
    super.setReuseAddress(reuseAddress);
    return this;
  }

  @Override
  public NetClientOptions setTrafficClass(int trafficClass) {
    super.setTrafficClass(trafficClass);
    return this;
  }

  @Override
  public NetClientOptions setTcpNoDelay(boolean tcpNoDelay) {
    super.setTcpNoDelay(tcpNoDelay);
    return this;
  }

  @Override
  public NetClientOptions setTcpKeepAlive(boolean tcpKeepAlive) {
    super.setTcpKeepAlive(tcpKeepAlive);
    return this;
  }

  @Override
  public NetClientOptions setSoLinger(int soLinger) {
    super.setSoLinger(soLinger);
    return this;
  }

  @Override
  public NetClientOptions setUsePooledBuffers(boolean usePooledBuffers) {
    super.setUsePooledBuffers(usePooledBuffers);
    return this;
  }

  @Override
  public NetClientOptions setIdleTimeout(int idleTimeout) {
    super.setIdleTimeout(idleTimeout);
    return this;
  }

  @Override
  public NetClientOptions setSsl(boolean ssl) {
    super.setSsl(ssl);
    return this;
  }

  @Override
  public NetClientOptions setKeyStoreOptions(KeyStoreOptions keyStore) {
    super.setKeyStoreOptions(keyStore);
    return this;
  }

  @Override
  public NetClientOptions setTrustStoreOptions(TrustStoreOptions trustStore) {
    super.setTrustStoreOptions(trustStore);
    return this;
  }

  @Override
  public NetClientOptions addEnabledCipherSuite(String suite) {
    super.addEnabledCipherSuite(suite);
    return this;
  }

  @Override
  public NetClientOptions setTrustAll(boolean trustAll) {
    super.setTrustAll(trustAll);
    return this;
  }

  @Override
  public NetClientOptions setConnectTimeout(int connectTimeout) {
    super.setConnectTimeout(connectTimeout);
    return this;
  }

  public NetClientOptions setReconnectAttempts(int attempts) {
    if (attempts < -1) {
      throw new IllegalArgumentException("reconnect attempts must be >= -1");
    }
    this.reconnectAttempts = attempts;
    return this;
  }

  public int getReconnectAttempts() {
    return reconnectAttempts;
  }

  public NetClientOptions setReconnectInterval(long interval) {
    if (interval < 1) {
      throw new IllegalArgumentException("reconnect interval nust be >= 1");
    }
    this.reconnectInterval = interval;
    return this;
  }

  public long getReconnectInterval() {
    return reconnectInterval;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NetClientOptions)) return false;
    if (!super.equals(o)) return false;

    NetClientOptions that = (NetClientOptions) o;

    if (reconnectAttempts != that.reconnectAttempts) return false;
    if (reconnectInterval != that.reconnectInterval) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + reconnectAttempts;
    result = 31 * result + (int) (reconnectInterval ^ (reconnectInterval >>> 32));
    return result;
  }
}
