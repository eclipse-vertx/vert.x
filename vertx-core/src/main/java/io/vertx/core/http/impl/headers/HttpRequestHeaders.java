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
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.HostAndPort;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class HttpRequestHeaders extends HttpHeaders {

  private HttpMethod method;
  private HostAndPort authority;
  private String uri;
  private String scheme;

  public HttpRequestHeaders(Headers<CharSequence, CharSequence, ?> headers) {
    super(headers);
  }

  HttpRequestHeaders(boolean mutable, Headers<CharSequence, CharSequence, ?> headers) {
    super(mutable, headers);
  }

  public HttpHeaders path(String path) {
    this.uri = path;
    return this;
  }

  public String path() {
    return uri;
  }

  public HttpHeaders method(HttpMethod method) {
    this.method = method;
    return this;
  }

  public HttpMethod method() {
    return method;
  }

  public HttpHeaders authority(HostAndPort authority) {
    this.authority = authority;
    return this;
  }

  public HostAndPort authority() {
    return authority;
  }

  public String scheme() {
    return scheme;
  }

  public HttpHeaders scheme(String scheme) {
    this.scheme = scheme;
    return this;
  }

  public boolean validate() {
    CharSequence methodHeader = headers.get(io.vertx.core.http.HttpHeaders.PSEUDO_METHOD);
    if (methodHeader == null) {
      return false;
    }
    HttpMethod method = HttpMethod.valueOf(methodHeader.toString());

    CharSequence schemeHeader = headers.get(io.vertx.core.http.HttpHeaders.PSEUDO_SCHEME);
    String scheme = schemeHeader != null ? schemeHeader.toString() : null;

    CharSequence pathHeader = headers.get(io.vertx.core.http.HttpHeaders.PSEUDO_PATH);
    String uri = pathHeader != null ? pathHeader.toString() : null;

    HostAndPort authority;
    CharSequence authorityHeader = headers.get(io.vertx.core.http.HttpHeaders.PSEUDO_AUTHORITY);
    if (authorityHeader != null) {
      String authorityHeaderAsString = authorityHeader.toString();
      authority = HostAndPort.parseAuthority(authorityHeaderAsString, -1);
    } else {
      authority = null;
    }

    HostAndPort authorityPresence;
    CharSequence hostHeader = headers.get(io.vertx.core.http.HttpHeaders.HOST);
    if (authority == null && hostHeader != null) {
      authorityPresence = HostAndPort.parseAuthority(hostHeader.toString(), -1);
    } else {
      authorityPresence = authority;
    }

    if (method == HttpMethod.CONNECT) {
      if (scheme != null || uri != null || authorityPresence == null) {
        return false;
      }
    } else {
      if (scheme == null || uri == null || uri.isEmpty()) {
        return false;
      }
    }

    boolean hasAuthority = authorityHeader != null || hostHeader != null;
    if (hasAuthority) {
      if (authorityPresence == null) {
        // Malformed authority
        return false;
      }
      if (hostHeader != null) {
        HostAndPort host = HostAndPort.parseAuthority(hostHeader.toString(), -1);
        if (host == null || (!authorityPresence.host().equals(host.host()) || authorityPresence.port() != host.port())) {
          return false;
        }
      }
    }

    this.method = method;
    this.uri = uri;
    this.authority = authority;
    this.scheme = scheme;

    return true;
  }

  public HttpHeaders sanitize() {
    headers.remove(io.vertx.core.http.HttpHeaders.PSEUDO_METHOD);
    headers.remove(io.vertx.core.http.HttpHeaders.PSEUDO_PATH);
    headers.remove(io.vertx.core.http.HttpHeaders.PSEUDO_SCHEME);
    headers.remove(io.vertx.core.http.HttpHeaders.PSEUDO_AUTHORITY);
    return this;
  }

  public void prepare() {
    boolean ssl = "ssl".equals(scheme);
    if (method != null) {
      headers.set(io.vertx.core.http.HttpHeaders.PSEUDO_METHOD, method.toString());
    }
    if (uri != null) {
      headers.set(io.vertx.core.http.HttpHeaders.PSEUDO_PATH, uri);
    }
    if (scheme != null) {
      headers.set(io.vertx.core.http.HttpHeaders.PSEUDO_SCHEME, scheme);
    }
    if (authority != null) {
      headers.set(io.vertx.core.http.HttpHeaders.PSEUDO_AUTHORITY, authority.toString(ssl));
    }
    if (scheme != null) {
      headers.set(io.vertx.core.http.HttpHeaders.PSEUDO_SCHEME, scheme);
    }
  }

  @Override
  HttpHeaders copy(boolean mutable, Headers<CharSequence, CharSequence, ?> headers) {
    return new HttpRequestHeaders(mutable, new DefaultHttp2Headers().setAll(headers));
  }
}
