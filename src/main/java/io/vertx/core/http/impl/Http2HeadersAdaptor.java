/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.core.MultiMap;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class Http2HeadersAdaptor implements MultiMap {

  static CharSequence toLowerCase(CharSequence s) {
    StringBuilder buffer = null;
    int len = s.length();
    for (int index = 0; index < len; index++) {
      char c = s.charAt(index);
      if (c >= 'A' && c <= 'Z') {
        if (buffer == null) {
          buffer = new StringBuilder(s);
        }
        buffer.setCharAt(index, (char)(c + ('a' - 'A')));
      }
    }
    if (buffer != null) {
      return buffer.toString();
    } else {
      return s;
    }
  }

  private final Http2Headers headers;
  private Set<String> names;
  private List<Map.Entry<String, String>> entries;

  public Http2HeadersAdaptor(Http2Headers headers) {
    this.headers = headers;
  }

  @Override
  public String get(String name) {
    CharSequence val = headers.get(toLowerCase(name));
    return val != null ? val.toString() : null;
  }

  @Override
  public List<String> getAll(String name) {
    List<CharSequence> all = headers.getAll(toLowerCase(name));
    if (all != null) {
      return new AbstractList<String>() {
        @Override
        public String get(int index) {
          return all.get(index).toString();
        }
        @Override
        public int size() {
          return all.size();
        }
      };
    }
    return null;
  }

  @Override
  public List<Map.Entry<String, String>> entries() {
    if (entries == null) {
      entries = headers.names()
          .stream()
          .map(name -> new AbstractMap.SimpleEntry<>(name.toString(), headers.get(name).toString()))
          .collect(Collectors.toList());
    }
    return entries;
  }

  @Override
  public boolean contains(String name) {
    return headers.contains(toLowerCase(name));
  }

  @Override
  public boolean isEmpty() {
    return headers.isEmpty();
  }

  @Override
  public Set<String> names() {
    if (names == null) {
      names = new AbstractSet<String>() {
        @Override
        public Iterator<String> iterator() {
          Iterator<CharSequence> it = headers.names().iterator();
          return new Iterator<String>() {
            @Override
            public boolean hasNext() {
              return it.hasNext();
            }
            @Override
            public String next() {
              return it.next().toString();
            }
          };
        }
        @Override
        public int size() {
          return headers.size();
        }
      };
    }
    return names;
  }

  @Override
  public MultiMap add(String name, String value) {
    headers.add(toLowerCase(name), value);
    return this;
  }

  @Override
  public MultiMap add(String name, Iterable<String> values) {
    headers.add(toLowerCase(name), values);
    return this;
  }

  @Override
  public MultiMap addAll(MultiMap headers) {
    for (Map.Entry<String, String> entry: headers.entries()) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public MultiMap addAll(Map<String, String> map) {
    for (Map.Entry<String, String> entry: map.entrySet()) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public MultiMap set(String name, String value) {
    headers.set(toLowerCase(name), value);
    return this;
  }

  @Override
  public MultiMap set(String name, Iterable<String> values) {
    headers.set(toLowerCase(name), values);
    return this;
  }

  @Override
  public MultiMap setAll(MultiMap httpHeaders) {
    clear();
    for (Map.Entry<String, String> entry: httpHeaders) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public MultiMap remove(String name) {
    headers.remove(toLowerCase(name));
    return this;
  }

  @Override
  public MultiMap clear() {
    headers.clear();
    return this;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    return entries().iterator();
  }

  @Override
  public int size() {
    return names().size();
  }

  @Override
  public MultiMap setAll(Map<String, String> headers) {
    for (Map.Entry<String, String> entry: headers.entrySet()) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public String get(CharSequence name) {
    CharSequence val = headers.get(toLowerCase(name));
    return val != null ? val.toString() : null;
  }

  @Override
  public List<String> getAll(CharSequence name) {
    List<CharSequence> all = headers.getAll(toLowerCase(name));
    return all != null ? all.stream().map(CharSequence::toString).collect(Collectors.toList()) : null;
  }

  @Override
  public boolean contains(CharSequence name) {
    return headers.contains(toLowerCase(name));
  }

  @Override
  public MultiMap add(CharSequence name, CharSequence value) {
    headers.add(toLowerCase(name), value);
    return this;
  }

  @Override
  public MultiMap add(CharSequence name, Iterable<CharSequence> values) {
    headers.add(toLowerCase(name), values);
    return this;
  }

  @Override
  public MultiMap set(CharSequence name, CharSequence value) {
    headers.set(toLowerCase(name), value);
    return this;
  }

  @Override
  public MultiMap set(CharSequence name, Iterable<CharSequence> values) {
    headers.set(toLowerCase(name), values);
    return this;
  }

  @Override
  public MultiMap remove(CharSequence name) {
    headers.remove(toLowerCase(name));
    return this;
  }
}
