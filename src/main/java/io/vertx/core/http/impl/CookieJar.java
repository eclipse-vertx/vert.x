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

import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

import java.util.*;

/**
 * Vert.x cookie jar implementation. A Cookie Jar is a simple wrapped list that behaves like a Set with one difference.
 * On adding an existing item, the underlying list is updated.
 *
 * This call also provides extra semantics for handling invalidation of cookies, which aren't plain removals as well as
 * quick search utilities to find all matching cookies by name or by unique key.
 *
 * @author <a href="http://pmlopes@gmail.com">Paulo Lopes</a>
 */
public class CookieJar implements Set<ServerCookie> {

  private List<ServerCookie> list;

  public CookieJar() {
  }

  public CookieJar(CharSequence cookieHeader) {
    if (cookieHeader != null) {
      Set<io.netty.handler.codec.http.cookie.Cookie> nettyCookies = ServerCookieDecoder.STRICT.decode(cookieHeader.toString());
      list = new ArrayList<>(nettyCookies.size());
      for (io.netty.handler.codec.http.cookie.Cookie cookie : nettyCookies) {
        list.add(new CookieImpl(cookie));
      }
    }
  }

  private CookieJar(List<ServerCookie> list) {
    this.list = list;
  }

  private void initIfNeeded() {
    if (list == null) {
      list = new ArrayList<>(4);
    }
  }

  @Override
  public int size() {
    if (list == null) {
      return 0;
    }
    return list.size();
  }

  @Override
  public boolean isEmpty() {
    if (list == null) {
      return true;
    }
    return list.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    if (list == null) {
      return false;
    }

    if (!(o instanceof ServerCookie)) {
      return false;
    }

    ServerCookie needle = (ServerCookie) o;

    for (ServerCookie cookie : list) {
      if (cookie.compareTo(needle.getName(), needle.getDomain(), needle.getPath()) == 0) {
        return true;
      }
    }

    return false;
  }

  @Override
  public Iterator<ServerCookie> iterator() {
    initIfNeeded();
    return list.iterator();
  }

  @Override
  public Object[] toArray() {
    initIfNeeded();
    return list.toArray();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    initIfNeeded();
    return list.toArray(a);
  }

  @Override
  public boolean add(ServerCookie cookie) {
    if (cookie == null) {
      return false;
    }

    initIfNeeded();

    for (int i = 0; i < list.size(); i++) {
      int cmp = list.get(i).compareTo(cookie.getName(), cookie.getDomain(), cookie.getPath());

      if (cmp > 0) {
        // insert
        list.add(i, cookie);
        return true;
      }
      if (cmp == 0) {
        // replace
        list.set(i, cookie);
        return true;
      }
    }
    // reached the end
    list.add(cookie);
    return true;
  }

  @Override
  public boolean remove(Object o) {
    if (list == null) {
      return false;
    }
    return list.remove(o);
  }

  public CookieJar removeOrInvalidateAll(String name, boolean invalidate) {
    if (list == null) {
      return null;
    }

    Iterator<ServerCookie> it = list.iterator();
    List<ServerCookie> collector = null;

    while (it.hasNext()) {
      ServerCookie cookie = it.next();
      if (cookie.getName().equals(name)) {
        removeOrInvalidateCookie(it, cookie, invalidate);
        if (collector == null) {
          collector = new ArrayList<>(Math.min(4, list.size()));
        }
        collector.add(cookie);
      }
    }

    if (collector != null) {
      return new CookieJar(collector);
    }
    return null;
  }

  public ServerCookie removeOrInvalidate(String name, String domain, String path, boolean invalidate) {
    if (list == null) {
      return null;
    }

    Iterator<ServerCookie> it = list.iterator();
    while (it.hasNext()) {
      ServerCookie cookie = it.next();
      if (cookie.compareTo(name, domain, path) == 0) {
        removeOrInvalidateCookie(it, cookie, invalidate);
        return cookie;
      }
    }

    return null;
  }

  public ServerCookie removeOrInvalidate(String name, boolean invalidate) {
    if (list == null) {
      return null;
    }

    Iterator<ServerCookie> it = list.iterator();
    while (it.hasNext()) {
      ServerCookie cookie = it.next();
      if (cookie.getName().equals(name)) {
        removeOrInvalidateCookie(it, cookie, invalidate);
        return cookie;
      }
    }

    return null;
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

  @Override
  public boolean containsAll(Collection<?> c) {
    initIfNeeded();
    return list.containsAll(c);
  }

  @Override
  public boolean addAll(Collection<? extends ServerCookie> c) {
    boolean modified = false;
    for (ServerCookie e : c)
      if (add(e))
        modified = true;
    return modified;
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    initIfNeeded();
    return list.retainAll(c);
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    if (list == null) {
      return false;
    }
    return list.removeAll(c);
  }

  @Override
  public void clear() {
    if (list != null) {
      list.clear();
    }
  }

  @Override
  public String toString() {
    if (list == null) {
      return "[]";
    }
    return list.toString();
  }

  public CookieJar get(String name) {
    if (list == null) {
      return null;
    }
    List<ServerCookie> subList = null;

    for (ServerCookie cookie : list) {
      if (subList == null) {
        subList = new ArrayList<>(Math.min(4, list.size()));
      }
      if (cookie.getName().equals(name)) {
        subList.add(cookie);
      }
    }

    if (subList != null) {
      return new CookieJar(subList);
    }
    return null;
  }

  public ServerCookie get(String name, String domain, String path) {
    if (list == null) {
      return null;
    }

    for (ServerCookie cookie : list) {
      if (cookie.compareTo(name, domain, path) == 0) {
        return cookie;
      }
    }

    return null;
  }
}
