/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net;

import io.netty.handler.ssl.OpenSsl;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * Configures a {@link TCPSSLOptions} to use OpenSsl.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true, publicConverter = false)
public class OpenSSLEngineOptions extends SSLEngineOptions {

  /**
   * @return when OpenSSL is available
   */
  public static boolean isAvailable() {
    return OpenSsl.isAvailable();
  }

  /**
   * @return when alpn support is available via OpenSSL engine
   */
  public static boolean isAlpnAvailable() {
    return OpenSsl.isAlpnSupported();
  }

  /**
   * Default value of whether session cache is enabled in open SSL session server context = true
   */
  public static final boolean DEFAULT_SESSION_CACHE_ENABLED = true;

  private boolean sessionCacheEnabled;

  public OpenSSLEngineOptions() {
    sessionCacheEnabled = DEFAULT_SESSION_CACHE_ENABLED;
  }

  public OpenSSLEngineOptions(JsonObject json) {
    OpenSSLEngineOptionsConverter.fromJson(json, this);
  }

  public OpenSSLEngineOptions(OpenSSLEngineOptions other) {
    this.sessionCacheEnabled = other.isSessionCacheEnabled();
  }

  /**
   * Set whether session cache is enabled in open SSL session server context
   *
   * @param sessionCacheEnabled true if session cache is enabled
   * @return a reference to this, so the API can be used fluently
   */
  public OpenSSLEngineOptions setSessionCacheEnabled(boolean sessionCacheEnabled) {
    this.sessionCacheEnabled = sessionCacheEnabled;
    return this;
  }

  /**
   * Whether session cache is enabled in open SSL session server context
   *
   * @return true if session cache is enabled
   */
  public boolean isSessionCacheEnabled() {
    return sessionCacheEnabled;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OpenSSLEngineOptions)) return false;

    OpenSSLEngineOptions that = (OpenSSLEngineOptions) o;

    if (sessionCacheEnabled != that.sessionCacheEnabled) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return sessionCacheEnabled ? 1 : 0;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    OpenSSLEngineOptionsConverter.toJson(this, json);
    return json;
  }

  @Override
  public OpenSSLEngineOptions clone() {
    return new OpenSSLEngineOptions(this);
  }
}
