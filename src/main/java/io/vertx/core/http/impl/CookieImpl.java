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
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * Vert.x cookie implementation
 *
 * @author <a href="http://pmlopes@gmail.com">Paulo Lopes</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CookieImpl implements ServerCookie {

  static Map<String, ServerCookie> extractCookies(CharSequence cookieHeader) {
    if (cookieHeader != null) {
      Set<io.netty.handler.codec.http.cookie.Cookie> nettyCookies = ServerCookieDecoder.STRICT.decode(cookieHeader.toString());
      Map<String, ServerCookie> cookies = new HashMap<>(nettyCookies.size());
      for (io.netty.handler.codec.http.cookie.Cookie cookie : nettyCookies) {
        ServerCookie ourCookie = new CookieImpl(cookie);
        cookies.put(ourCookie.getName(), ourCookie);
      }
      return cookies;
    } else {
      return new HashMap<>(4);
    }
  }


  static Cookie removeCookie(Map<String, ServerCookie> cookieMap, String name, boolean invalidate) {
    ServerCookie cookie = cookieMap.get(name);
    if (cookie != null) {
      if (invalidate && cookie.isFromUserAgent()) {
        // in the case the cookie was passed from the User Agent
        // we need to expire it and sent it back to it can be
        // invalidated
        cookie.setMaxAge(0L);
        // void the value for user-agents that still read the cookie
        cookie.setValue("");
      } else {
        // this was a temporary cookie so we can safely remove it
        cookieMap.remove(name);
      }
    }
    return cookie;
  }

  private final io.netty.handler.codec.http.cookie.Cookie nettyCookie;
  private boolean changed;
  private boolean fromUserAgent;
  // extension features
  private CookieSameSite sameSite;

  public CookieImpl(String name, String value) {
    this.nettyCookie = new DefaultCookie(name, value);
    this.changed = true;
  }

  public CookieImpl(io.netty.handler.codec.http.cookie.Cookie nettyCookie) {
    this.nettyCookie = nettyCookie;
    fromUserAgent = true;
  }

  @Override
  public String getValue() {
    return nettyCookie.value();
  }

  @Override
  public Cookie setValue(final String value) {
    nettyCookie.setValue(value);
    this.changed = true;
    return this;
  }

  @Override
  public String getName() {
    return nettyCookie.name();
  }

  @Override
  public Cookie setDomain(final String domain) {
    nettyCookie.setDomain(domain);
    this.changed = true;
    return this;
  }

  @Override
  public String getDomain() {
    return nettyCookie.domain();
  }

  @Override
  public Cookie setPath(final String path) {
    nettyCookie.setPath(path);
    this.changed = true;
    return this;
  }

  @Override
  public String getPath() {
    return nettyCookie.path();
  }

  @Override
  public Cookie setMaxAge(final long maxAge) {
    nettyCookie.setMaxAge(maxAge);
    this.changed = true;
    return this;
  }

  @Override
  public long getMaxAge() {
    return nettyCookie.maxAge();
  }

  @Override
  public Cookie setSecure(final boolean secure) {
    nettyCookie.setSecure(secure);
    this.changed = true;
    return this;
  }

  @Override
  public boolean isSecure() {
    return nettyCookie.isSecure();
  }

  @Override
  public Cookie setHttpOnly(final boolean httpOnly) {
    nettyCookie.setHttpOnly(httpOnly);
    this.changed = true;
    return this;
  }

  @Override
  public boolean isHttpOnly() {
    return nettyCookie.isHttpOnly();
  }

  @Override
  public Cookie setSameSite(final CookieSameSite sameSite) {
    this.sameSite = sameSite;
    this.changed = true;
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
    return changed;
  }

  public void setChanged(boolean changed) {
    this.changed = changed;
  }

  public boolean isFromUserAgent() {
    return fromUserAgent;
  }
}
