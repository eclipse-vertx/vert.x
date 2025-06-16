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

import io.netty.handler.codec.Headers;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.internal.http.HttpHeadersInternal;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class Http2HeadersAdaptor implements MultiMap {

  private final boolean mutable;
  private final Headers<CharSequence, CharSequence, ?> headers;

  public Http2HeadersAdaptor(Headers<CharSequence, CharSequence, ?> headers) {
    this(true, headers);
  }

  private Http2HeadersAdaptor(boolean mutable, Headers<CharSequence, CharSequence, ?> headers) {

    List<CharSequence> cookies = headers.getAll(HttpHeaderNames.COOKIE);
    if (cookies != null && cookies.size() > 1) {
      // combine the cookie values into 1 header entry.
      // https://tools.ietf.org/html/rfc7540#section-8.1.2.5
      String value = cookies.stream().collect(Collectors.joining("; "));
      headers.set(HttpHeaderNames.COOKIE, value);
    }

    this.mutable = mutable;
    this.headers = headers;
  }

  public Http2HeadersAdaptor status(AsciiString status) {
    if (headers instanceof Http2Headers) {
      ((Http2Headers) headers).status(status);
    } else {
      throw new UnsupportedOperationException("Implement me");
    }
    return this;
  }

  public CharSequence status() {
    if (headers instanceof Http2Headers) {
      return ((Http2Headers) headers).status();
    } else {
      throw new UnsupportedOperationException("Implement me");
    }
  }

  public Http2HeadersAdaptor path(CharSequence path) {
    if (headers instanceof Http2Headers) {
      ((Http2Headers) headers).path(path);
    } else {
      throw new UnsupportedOperationException("Implement me");
    }
    return this;
  }

  public CharSequence path() {
    if (headers instanceof Http2Headers) {
      return ((Http2Headers) headers).path();
    } else {
      throw new UnsupportedOperationException("Implement me");
    }
  }

  public Http2HeadersAdaptor method(CharSequence method) {
    if (headers instanceof Http2Headers) {
      ((Http2Headers) headers).method(method);
    } else {
      throw new UnsupportedOperationException("Implement me");
    }
    return this;
  }

  public CharSequence method() {
    if (headers instanceof Http2Headers) {
      return ((Http2Headers) headers).method();
    } else {
      throw new UnsupportedOperationException("Implement me");
    }
  }

  public Http2HeadersAdaptor authority(CharSequence authority) {
    if (headers instanceof Http2Headers) {
      ((Http2Headers) headers).authority(authority);
    } else {
      throw new UnsupportedOperationException("Implement me");
    }
    return this;
  }

  public CharSequence authority() {
    if (headers instanceof Http2Headers) {
      return ((Http2Headers) headers).authority();
    } else {
      throw new UnsupportedOperationException("Implement me");
    }
  }

  public Http2HeadersAdaptor scheme(CharSequence scheme) {
    if (headers instanceof Http2Headers) {
      ((Http2Headers) headers).scheme(scheme);
    } else {
      throw new UnsupportedOperationException("Implement me");
    }
    return this;
  }

  public Headers<CharSequence, CharSequence, ?> unwrap() {
    return headers;
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
    if (headers instanceof Http2Headers) {
      return ((Http2Headers)headers).contains(HttpUtils.toLowerCase(name), value, caseInsensitive);
    } else {
      throw new UnsupportedOperationException("Implement me");
    }
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
  public Http2HeadersAdaptor add(String name, String value) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    if (!HttpHeadersInternal.DISABLE_HTTP_HEADERS_VALIDATION) {
      HttpUtils.validateHeader(name, value);
    }
    headers.add(HttpUtils.toLowerCase(name), value);
    return this;
  }

  @Override
  public Http2HeadersAdaptor add(String name, Iterable<String> values) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    if (!HttpHeadersInternal.DISABLE_HTTP_HEADERS_VALIDATION) {
      HttpUtils.validateHeader(name, values);
    }
    headers.add(HttpUtils.toLowerCase(name), values);
    return this;
  }

  @Override
  public Http2HeadersAdaptor addAll(MultiMap headers) {
    for (Map.Entry<String, String> entry: headers.entries()) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public Http2HeadersAdaptor addAll(Map<String, String> map) {
    for (Map.Entry<String, String> entry: map.entrySet()) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public Http2HeadersAdaptor set(String name, String value) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    if (!HttpHeadersInternal.DISABLE_HTTP_HEADERS_VALIDATION) {
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
  public Http2HeadersAdaptor set(String name, Iterable<String> values) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    if (!HttpHeadersInternal.DISABLE_HTTP_HEADERS_VALIDATION) {
      HttpUtils.validateHeader(name, values);
    }
    headers.set(HttpUtils.toLowerCase(name), values);
    return this;
  }

  @Override
  public Http2HeadersAdaptor setAll(MultiMap httpHeaders) {
    clear();
    for (Map.Entry<String, String> entry: httpHeaders) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public Http2HeadersAdaptor remove(String name) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    headers.remove(HttpUtils.toLowerCase(name));
    return this;
  }

  @Override
  public Http2HeadersAdaptor clear() {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
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
            if (!mutable) {
              throw new IllegalStateException("Read only");
            }
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
  public Http2HeadersAdaptor setAll(Map<String, String> headers) {
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
    if (headers instanceof Http2Headers) {
      return ((Http2Headers)headers).contains(HttpUtils.toLowerCase(name), value, caseInsensitive);
    } else {
      throw new UnsupportedOperationException("Implement me");
    }
  }

  @Override
  public Http2HeadersAdaptor add(CharSequence name, CharSequence value) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    if (!HttpHeadersInternal.DISABLE_HTTP_HEADERS_VALIDATION) {
      HttpUtils.validateHeader(name, value);
    }
    headers.add(HttpUtils.toLowerCase(name), value);
    return this;
  }

  @Override
  public Http2HeadersAdaptor add(CharSequence name, Iterable<CharSequence> values) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    if (!HttpHeadersInternal.DISABLE_HTTP_HEADERS_VALIDATION) {
      HttpUtils.validateHeader(name, values);
    }
    headers.add(HttpUtils.toLowerCase(name), values);
    return this;
  }

  @Override
  public Http2HeadersAdaptor set(CharSequence name, CharSequence value) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    if (!HttpHeadersInternal.DISABLE_HTTP_HEADERS_VALIDATION) {
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
  public Http2HeadersAdaptor set(CharSequence name, Iterable<CharSequence> values) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    if (!HttpHeadersInternal.DISABLE_HTTP_HEADERS_VALIDATION) {
      HttpUtils.validateHeader(name, values);
    }
    headers.set(HttpUtils.toLowerCase(name), values);
    return this;
  }

  @Override
  public Http2HeadersAdaptor remove(CharSequence name) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
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

  @Override
  public boolean isMutable() {
    return mutable;
  }

  @Override
  public MultiMap copy(boolean mutable) {
    if (!this.mutable && ! mutable) {
      return this;
    }
    return new Http2HeadersAdaptor(mutable, new DefaultHttp2Headers().setAll(headers));
  }
}
