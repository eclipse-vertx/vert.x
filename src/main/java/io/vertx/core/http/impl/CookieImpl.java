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

import java.util.*;


/**
 * Vert.x cookie implementation
 *
 * @author <a href="http://pmlopes@gmail.com">Paulo Lopes</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CookieImpl implements ServerCookie {

  static Set<ServerCookie> extractCookies(CharSequence cookieHeader) {
    Set<ServerCookie> cookies = new TreeSet<>();
    if (cookieHeader != null) {
      Set<io.netty.handler.codec.http.cookie.Cookie> nettyCookies = ServerCookieDecoder.STRICT.decode(cookieHeader.toString());
      for (io.netty.handler.codec.http.cookie.Cookie cookie : nettyCookies) {
        cookies.add(new CookieImpl(cookie));
      }
    }
    return cookies;
  }

  static void addCookie(Set<ServerCookie> serverCookies, ServerCookie cookie) {
    // given the semantics of a set, an existing cookie would not be replaced.
    // we ensure that we remove any existing same key cookie before we add
    // the updated value.
    serverCookies.remove(cookie);
    serverCookies.add(cookie);
  }


  static Cookie removeCookie(Set<ServerCookie> serverCookies, String name, String domain, String path, boolean invalidate) {
    Iterator<ServerCookie> it = serverCookies.iterator();
    while (it.hasNext()) {
      ServerCookie cookie = it.next();
      if (cookie.compareTo(name, domain, path) == 0) {
        removeOrInvalidateCookie(it, cookie, invalidate);
        return cookie;
      }
    }
    return null;
  }

  public static Cookie removeCookie(Set<ServerCookie> serverCookies, String name, boolean invalidate) {
    Iterator<ServerCookie> it = serverCookies.iterator();
    while (it.hasNext()) {
      ServerCookie cookie = it.next();
      if (cookie.getName().equals(name)) {
        removeOrInvalidateCookie(it, cookie, invalidate);
        return cookie;
      }
    }
    return null;
  }

  static Set<Cookie> removeCookies(Set<ServerCookie> serverCookies, String name, boolean invalidate) {
    Iterator<ServerCookie> it = serverCookies.iterator();
    Set<Cookie> found = null;
    while (it.hasNext()) {
      ServerCookie cookie = it.next();
      if (name.equals(cookie.getName())) {
        if (found == null) {
          found = new TreeSet<>();
        }
        found.add(cookie);
        removeOrInvalidateCookie(it, cookie, invalidate);
      }
    }
    return found;
  }

  private static void removeOrInvalidateCookie(Iterator<ServerCookie> it, ServerCookie cookie, boolean invalidate) {
    if (invalidate && cookie.isFromUserAgent()) {
      // in the case the cookie was passed from the User Agent
      // we need to expire it and sent it back to it can be
      // invalidated
      cookie.setMaxAge(0L);
      // void the value for user-agents that still read the cookie
      cookie.setValue("");
    } else {
      // this was a temporary cookie, we can safely remove it
      it.remove();
    }
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

  @Override
  public int compareTo(Cookie c) {
    return compareTo(c.getName(), c.getDomain(), c.getPath());
  }

  @Override
  public int compareTo(String name, String domain, String path) {
    int v = getName().compareTo(name);
    if (v != 0) {
      return v;
    }

    if (getPath() == null) {
      if (path != null) {
        return -1;
      }
    } else if (path == null) {
      return 1;
    } else {
      v = getPath().compareTo(path);
      if (v != 0) {
        return v;
      }
    }

    if (getDomain() == null) {
      if (domain != null) {
        return -1;
      }
    } else if (domain == null) {
      return 1;
    } else {
      v = getDomain().compareToIgnoreCase(domain);
      return v;
    }

    return 0;
  }
}
