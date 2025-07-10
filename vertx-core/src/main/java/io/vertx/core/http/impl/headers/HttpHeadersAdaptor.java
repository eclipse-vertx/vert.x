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
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.internal.http.HttpHeadersInternal;
import io.vertx.core.net.HostAndPort;

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
public abstract class HttpHeadersAdaptor<T extends Headers<CharSequence, CharSequence, T>> implements VertxHttpHeaders {

  private final boolean mutable;
  protected final T headers;

  protected abstract boolean containsHeader(CharSequence name, CharSequence value, boolean caseInsensitive);

  public HttpHeadersAdaptor(T headers) {
    this(true, headers);
  }

  protected HttpHeadersAdaptor(boolean mutable, T headers) {

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

  private Integer status;
  private HttpMethod method;
  private HostAndPort authority;
  private String uri;
  private String scheme;

  public boolean validate(boolean isRequest) {
    if (isRequest) {
      CharSequence methodHeader = headers.get(HttpHeaders.PSEUDO_METHOD);
      if (methodHeader == null) {
        return false;
      }
      HttpMethod method = HttpMethod.valueOf(methodHeader.toString());

      CharSequence schemeHeader = headers.get(HttpHeaders.PSEUDO_SCHEME);
      String scheme = schemeHeader != null ? schemeHeader.toString() : null;

      CharSequence pathHeader = headers.get(HttpHeaders.PSEUDO_PATH);
      String uri = pathHeader != null ? pathHeader.toString() : null;

      HostAndPort authority = null;
      String authorityHeaderAsString;
      CharSequence authorityHeader = headers.get(HttpHeaders.PSEUDO_AUTHORITY);
      if (authorityHeader != null) {
        authorityHeaderAsString = authorityHeader.toString();
        authority = HostAndPort.parseAuthority(authorityHeaderAsString, -1);
      }

      CharSequence hostHeader = headers.get(HttpHeaders.HOST);
      if (authority == null) {
        headers.remove(HttpHeaders.HOST);
        if (hostHeader != null) {
          authority = HostAndPort.parseAuthority(hostHeader.toString(), -1);
        }
      }

      if (method == HttpMethod.CONNECT) {
        if (scheme != null || uri != null || authority == null) {
          return false;
        }
      } else {
        if (scheme == null || uri == null || uri.isEmpty()) {
          return false;
        }
      }

      boolean hasAuthority = authorityHeader != null || hostHeader != null;
      if (hasAuthority) {
        if (authority == null) {
          return false;
        }
        if (hostHeader != null) {
          HostAndPort host = HostAndPort.parseAuthority(hostHeader.toString(), -1);
          if (host == null || (!authority.host().equals(host.host()) || authority.port() != host.port())) {
            return false;
          }
        }
      }

      this.method = method;
      this.uri = uri;
      this.authority = authority;
      this.scheme = scheme;

      return true;
    } else {
      CharSequence statusHeader = headers.get(HttpHeaders.PSEUDO_STATUS);
      if (statusHeader == null) {
        return false;
      }
      int status;
      try {
        status = Integer.parseInt(statusHeader.toString());
      } catch (NumberFormatException e) {
        return false;
      }
      this.status = status;
      return true;
    }
  }

  public HttpHeadersAdaptor<T> sanitize() {
    headers.remove(HttpHeaders.PSEUDO_METHOD);
    headers.remove(HttpHeaders.PSEUDO_PATH);
    headers.remove(HttpHeaders.PSEUDO_SCHEME);
    headers.remove(HttpHeaders.PSEUDO_AUTHORITY);
    headers.remove(HttpHeaders.PSEUDO_STATUS);
    return this;
  }

  public HttpHeadersAdaptor<T> prepare() {
    boolean ssl = "ssl".equals(scheme);
    if (method != null) {
      headers.set(HttpHeaders.PSEUDO_METHOD, method.toString());
    }
    if (uri != null) {
      headers.set(HttpHeaders.PSEUDO_PATH, uri);
    }
    if (scheme != null) {
      headers.set(HttpHeaders.PSEUDO_SCHEME, scheme);
    }
    if (authority != null) {
      headers.set(HttpHeaders.PSEUDO_AUTHORITY, authority.toString(ssl));
    }
    if (scheme != null) {
      headers.set(HttpHeaders.PSEUDO_SCHEME, scheme);
    }
    if (status != null) {
      headers.set(HttpHeaders.PSEUDO_STATUS, status.toString());
    }
    return this;
  }

  @Override
  public HttpHeadersAdaptor<T> status(CharSequence status) {
    if (status != null) {
      headers.set(HttpHeaders.PSEUDO_STATUS, status);
    } else {
      headers.remove(HttpHeaders.PSEUDO_STATUS);
    }
    return this;
  }

  public Integer status() {
    return status;
  }

  public HttpHeadersAdaptor<T> status(Integer status) {
    this.status = status;
    return this;
  }

  public HttpHeadersAdaptor<T> path(String path) {
    this.uri = path;
    return this;
  }

  public String path() {
    return uri;
  }

  @Deprecated
  @Override
  public void path(CharSequence value) {
    path(String.valueOf(value));
  }

  public HttpHeadersAdaptor<T> method(HttpMethod method) {
    this.method = method;
    return this;
  }

  public HttpMethod method() {
    return method;
  }

  public HttpHeadersAdaptor<T> authority(HostAndPort authority) {
    this.authority = authority;
    return this;
  }

  public HostAndPort authority() {
    return authority;
  }

  public String scheme() {
    return scheme;
  }

  public HttpHeadersAdaptor<T> scheme(String scheme) {
    this.scheme = scheme;
    return this;
  }

  @Override
  public void scheme(CharSequence value) {
    this.scheme(String.valueOf(value));
  }

  public T unwrap() {
    //TODO: remove me and rename getHeaders to unwrap!
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
    return containsHeader(HttpUtils.toLowerCase(name), value, caseInsensitive);
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
  public MultiMap add(String name, Iterable<String> values) {
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
  public MultiMap set(String name, Iterable<String> values) {
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
  public MultiMap setAll(MultiMap httpHeaders) {
    clear();
    for (Map.Entry<String, String> entry: httpHeaders) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public MultiMap remove(String name) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    headers.remove(HttpUtils.toLowerCase(name));
    return this;
  }

  @Override
  public MultiMap clear() {
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
    return containsHeader(HttpUtils.toLowerCase(name), value, caseInsensitive);
  }

  @Override
  public MultiMap add(CharSequence name, CharSequence value) {
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
  public MultiMap add(CharSequence name, Iterable<CharSequence> values) {
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
  public MultiMap set(CharSequence name, CharSequence value) {
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
  public MultiMap set(CharSequence name, Iterable<CharSequence> values) {
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
  public MultiMap remove(CharSequence name) {
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
}
