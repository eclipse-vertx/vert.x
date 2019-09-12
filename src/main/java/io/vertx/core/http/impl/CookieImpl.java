/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.vertx.core.http.Cookie;

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
    if (cookieMap == null) {
      return null;
    }
    ServerCookie cookie = cookieMap.get(name);
    if (cookie != null) {
      if (invalidate && cookie.isFromUserAgent()) {
        // in the case the cookie was passed from the User Agent
        // we need to expire it and sent it back to it can be
        // invalidated
        cookie.setMaxAge(0L);
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
  public Cookie setSecure(final boolean secure) {
    nettyCookie.setSecure(secure);
    this.changed = true;
    return this;
  }

  @Override
  public Cookie setHttpOnly(final boolean httpOnly) {
    nettyCookie.setHttpOnly(httpOnly);
    this.changed = true;
    return this;
  }

  @Override
  public String encode() {
    return ServerCookieEncoder.STRICT.encode(nettyCookie);
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
