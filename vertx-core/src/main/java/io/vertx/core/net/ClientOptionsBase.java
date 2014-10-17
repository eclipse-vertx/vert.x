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

import io.vertx.core.json.JsonObject;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class ClientOptionsBase extends TCPSSLOptions {

  public static final int DEFAULT_CONNECT_TIMEOUT = 60000;
  public static final boolean DEFAULT_TRUST_ALL = false;

  private int connectTimeout;
  private boolean trustAll;

  public ClientOptionsBase(ClientOptionsBase other) {
    super(other);
    this.connectTimeout = other.getConnectTimeout();
    this.trustAll = other.isTrustAll();
  }

  public ClientOptionsBase(JsonObject json) {
    super(json);
    this.connectTimeout = json.getInteger("connectTimeout", DEFAULT_CONNECT_TIMEOUT);
    this.trustAll = json.getBoolean("trustAll", DEFAULT_TRUST_ALL);
  }

  public ClientOptionsBase() {
    super();
    this.connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    this.trustAll = DEFAULT_TRUST_ALL;
  }

  public boolean isTrustAll() {
    return trustAll;
  }

  public ClientOptionsBase setTrustAll(boolean trustAll) {
    this.trustAll = trustAll;
    return this;
  }

  public int getConnectTimeout() {
    return connectTimeout;
  }

  public ClientOptionsBase setConnectTimeout(int connectTimeout) {
    if (connectTimeout < 0) {
      throw new IllegalArgumentException("connectTimeout must be >= 0");
    }
    this.connectTimeout = connectTimeout;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ClientOptionsBase)) return false;
    if (!super.equals(o)) return false;

    ClientOptionsBase that = (ClientOptionsBase) o;

    if (connectTimeout != that.connectTimeout) return false;
    if (trustAll != that.trustAll) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + connectTimeout;
    result = 31 * result + (trustAll ? 1 : 0);
    return result;
  }
}
