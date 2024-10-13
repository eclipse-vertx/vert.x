/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.http.impl.CookieImpl;

/**
 * Represents an HTTP Cookie.
 * <p>
 * All cookies must have a name and a value and can optionally have other fields set such as path, domain, etc.
 */
@DataObject
public interface Cookie {

  /**
   * Create a new cookie
   * @param name  the name of the cookie
   * @param value  the cookie value
   * @return the cookie
   */
  static Cookie cookie(String name, String value) {
    return new CookieImpl(name, value);
  }

  /**
   * @return the name of this cookie
   */
  String getName();

  /**
   * @return the value of this cookie
   */
  String getValue();

  /**
   * Sets the value of this cookie
   *
   * @param value The value to set
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  Cookie setValue(String value);

  /**
   * Sets the domain of this cookie
   *
   * @param domain The domain to use
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  Cookie setDomain(@Nullable String domain);

  /**
   * @return  the domain for the cookie
   */
  @Nullable
  String getDomain();

  /**
   * Sets the path of this cookie.
   *
   * @param path The path to use for this cookie
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  Cookie setPath(@Nullable String path);

  /**
   *
   * @return the path for this cookie
   */
  @Nullable
  String getPath();

  /**
   * Sets the maximum age of this cookie in seconds.
   * If an age of {@code 0} is specified, this cookie will be
   * automatically removed by browser because it will expire immediately.
   * If {@link Long#MIN_VALUE} is specified, this cookie will be removed when the
   * browser is closed.
   * If you don't set this the cookie will be a session cookie and be removed when the browser is closed.
   *
   * @param maxAge The maximum age of this cookie in seconds
   */
  @Fluent
  Cookie setMaxAge(long maxAge);

  /**
   * @return the maxAge of this cookie
   */
  long getMaxAge();

  /**
   * Sets the security getStatus of this cookie
   *
   * @param secure True if this cookie is to be secure, otherwise false
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  Cookie setSecure(boolean secure);

  /**
   * @return the security status of this cookie
   */
  boolean isSecure();

  /**
   * Determines if this cookie is HTTP only.
   * If set to true, this cookie cannot be accessed by a client
   * side script. However, this works only if the browser supports it.
   * For for information, please look
   * <a href="http://www.owasp.org/index.php/HTTPOnly">here</a>.
   *
   * @param httpOnly True if the cookie is HTTP only, otherwise false.
   */
  @Fluent
  Cookie setHttpOnly(boolean httpOnly);

  /**
   * @return the http only status of this cookie
   */
  boolean isHttpOnly();

  /**
   * Sets the same site of this cookie.
   *
   * @param policy The policy should be one of {@link CookieSameSite}.
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  Cookie setSameSite(CookieSameSite policy);

  /**
   * @return the SameSite policy of this cookie
   */
  @Nullable
  CookieSameSite getSameSite();

  /**
   * Encode the cookie to a string. This is what is used in the Set-Cookie header
   *
   * @return  the encoded cookie
   */
  String encode();

}
