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

package io.vertx.core.http.impl;

import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;


/**
 * Vert.x cookie implementation
 *
 * @author <a href="http://pmlopes@gmail.com">Paulo Lopes</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CookieImpl implements Cookie {

  private final io.netty.handler.codec.http.cookie.Cookie nettyCookie;

  // extension feature(s)
  private CookieSameSite sameSite;

  public CookieImpl(String name, String value) {
    this.nettyCookie = new DefaultCookie(name, value);
  }

  protected CookieImpl(io.netty.handler.codec.http.cookie.Cookie nettyCookie) {
    this.nettyCookie = nettyCookie;
  }

  @Override
  public String getValue() {
    return nettyCookie.value();
  }

  @Override
  public Cookie setValue(final String value) {
    nettyCookie.setValue(value);
    return this;
  }

  @Override
  public String getName() {
    return nettyCookie.name();
  }

  @Override
  public Cookie setDomain(final String domain) {
    nettyCookie.setDomain(domain);
    return this;
  }

  @Override
  public String getDomain() {
    return nettyCookie.domain();
  }

  @Override
  public Cookie setPath(final String path) {
    nettyCookie.setPath(path);
    return this;
  }

  @Override
  public String getPath() {
    return nettyCookie.path();
  }

  @Override
  public Cookie setMaxAge(final long maxAge) {
    nettyCookie.setMaxAge(maxAge);
    return this;
  }

  @Override
  public long getMaxAge() {
    return nettyCookie.maxAge();
  }

  @Override
  public Cookie setSecure(final boolean secure) {
    nettyCookie.setSecure(secure);
    return this;
  }

  @Override
  public boolean isSecure() {
    return nettyCookie.isSecure();
  }

  @Override
  public Cookie setHttpOnly(final boolean httpOnly) {
    nettyCookie.setHttpOnly(httpOnly);
    return this;
  }

  @Override
  public boolean isHttpOnly() {
    return nettyCookie.isHttpOnly();
  }

  @Override
  public Cookie setSameSite(final CookieSameSite sameSite) {
    this.sameSite = sameSite;
    return this;
  }

  @Override
  public CookieSameSite getSameSite() {
    return this.sameSite;
  }

  @Override
  public String encode() {
    if (sameSite != null) {
      return ServerCookieEncoder.STRICT.encode(nettyCookie) + "; SameSite=" + sameSite.toString();
    } else {
      return ServerCookieEncoder.STRICT.encode(nettyCookie);
    }
  }

  public boolean isChanged() {
    return true;
  }

  public boolean isFromUserAgent() {
    return false;
  }
}
