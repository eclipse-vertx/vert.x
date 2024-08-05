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
package io.vertx.core.http.impl.headers;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.incubator.codec.http3.Http3Headers;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.HttpUtils;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3HeadersAdaptor implements MultiMap {

  private final Http3Headers headers;

  public Http3HeadersAdaptor(Http3Headers headers) {

    List<CharSequence> cookies = headers.getAll(HttpHeaderNames.COOKIE);
    if (cookies != null && cookies.size() > 1) {
      // combine the cookie values into 1 header entry.
      // https://tools.ietf.org/html/rfc7540#section-8.1.2.5
      String value = cookies.stream().collect(Collectors.joining("; "));
      headers.set(HttpHeaderNames.COOKIE, value);
    }

    this.headers = headers;
  }

  @Override
  public String get(String name) {
    CharSequence val = headers.get(HttpUtils.toLowerCase(name));
    return val != null ? val.toString() : null;
  }

  @Override
  public List<String> getAll(String name) {
    List<CharSequence> all = headers.getAll(HttpUtils.toLowerCase(name));
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
  public boolean contains(String name) {
    return headers.contains(HttpUtils.toLowerCase(name));
  }

  @Override
  public boolean contains(String name, String value, boolean caseInsensitive) {
    return headers.contains(HttpUtils.toLowerCase(name), value, caseInsensitive);
  }

  @Override
  public boolean isEmpty() {
    return headers.isEmpty();
  }

  @Override
  public Set<String> names() {
    Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    for (Map.Entry<CharSequence, CharSequence> header : headers) {
      names.add(header.getKey().toString());
    }
    return names;
  }

  @Override
  public MultiMap add(String name, String value) {
    if (!io.vertx.core.http.HttpHeaders.DISABLE_HTTP_HEADERS_VALIDATION) {
      HttpUtils.validateHeader(name, value);
    }
    headers.add(HttpUtils.toLowerCase(name), value);
    return this;
  }

  @Override
  public MultiMap add(String name, Iterable<String> values) {
    if (!io.vertx.core.http.HttpHeaders.DISABLE_HTTP_HEADERS_VALIDATION) {
      HttpUtils.validateHeader(name, values);
    }
    headers.add(HttpUtils.toLowerCase(name), values);
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
    if (!io.vertx.core.http.HttpHeaders.DISABLE_HTTP_HEADERS_VALIDATION) {
      HttpUtils.validateHeader(name, value);
    }
    name = (String) HttpUtils.toLowerCase(name);
    if (value != null) {
      headers.set(name, value);
    } else {
      headers.remove(name);
    }
    return this;
  }

  @Override
  public MultiMap set(String name, Iterable<String> values) {
    if (!io.vertx.core.http.HttpHeaders.DISABLE_HTTP_HEADERS_VALIDATION) {
      HttpUtils.validateHeader(name, values);
    }
    headers.set(HttpUtils.toLowerCase(name), values);
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
    headers.remove(HttpUtils.toLowerCase(name));
    return this;
  }

  @Override
  public MultiMap clear() {
    headers.clear();
    return this;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    Iterator<Map.Entry<CharSequence, CharSequence>> i = headers.iterator();
    return new Iterator<Map.Entry<String, String>>() {
      @Override
      public boolean hasNext() {
        return i.hasNext();
      }
      @Override
      public Map.Entry<String, String> next() {
        Map.Entry<CharSequence, CharSequence> next = i.next();
        return new Map.Entry<String, String>() {
          @Override
          public String getKey() {
            return next.getKey().toString();
          }
          @Override
          public String getValue() {
            return next.getValue().toString();
          }
          @Override
          public String setValue(String value) {
            String old = next.getValue().toString();
            next.setValue(value);
            return old;
          }
          @Override
          public String toString() {
            return next.toString();
          }
        };
      }
    };
  }

  @Override
  public int size() {
    return names().size();
  }

  @Override
  public MultiMap setAll(Map<String, String> headers) {
    clear();
    for (Map.Entry<String, String> entry: headers.entrySet()) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public String get(CharSequence name) {
    CharSequence val = headers.get(HttpUtils.toLowerCase(name));
    return val != null ? val.toString() : null;
  }

  @Override
  public List<String> getAll(CharSequence name) {
    List<CharSequence> all = headers.getAll(HttpUtils.toLowerCase(name));
    return all != null ? all.stream().map(CharSequence::toString).collect(Collectors.toList()) : null;
  }

  @Override
  public boolean contains(CharSequence name) {
    return headers.contains(HttpUtils.toLowerCase(name));
  }

  @Override
  public boolean contains(CharSequence name, CharSequence value, boolean caseInsensitive) {
    return headers.contains(HttpUtils.toLowerCase(name), value, caseInsensitive);
  }

  @Override
  public MultiMap add(CharSequence name, CharSequence value) {
    if (!io.vertx.core.http.HttpHeaders.DISABLE_HTTP_HEADERS_VALIDATION) {
      HttpUtils.validateHeader(name, value);
    }
    headers.add(HttpUtils.toLowerCase(name), value);
    return this;
  }

  @Override
  public MultiMap add(CharSequence name, Iterable<CharSequence> values) {
    if (!io.vertx.core.http.HttpHeaders.DISABLE_HTTP_HEADERS_VALIDATION) {
      HttpUtils.validateHeader(name, values);
    }
    headers.add(HttpUtils.toLowerCase(name), values);
    return this;
  }

  @Override
  public MultiMap set(CharSequence name, CharSequence value) {
    if (!io.vertx.core.http.HttpHeaders.DISABLE_HTTP_HEADERS_VALIDATION) {
      HttpUtils.validateHeader(name, value);
    }
    name = HttpUtils.toLowerCase(name);
    if (value != null) {
      headers.set(name, value);
    } else {
      headers.remove(name);
    }
    return this;
  }

  @Override
  public MultiMap set(CharSequence name, Iterable<CharSequence> values) {
    if (!io.vertx.core.http.HttpHeaders.DISABLE_HTTP_HEADERS_VALIDATION) {
      HttpUtils.validateHeader(name, values);
    }
    headers.set(HttpUtils.toLowerCase(name), values);
    return this;
  }

  @Override
  public MultiMap remove(CharSequence name) {
    headers.remove(HttpUtils.toLowerCase(name));
    return this;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<CharSequence, CharSequence> header : headers) {
      sb.append(header).append('\n');
    }
    return sb.toString();
  }
}
