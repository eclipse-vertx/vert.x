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

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.observability.HttpRequest;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpRequestHead implements HttpRequest {

  public SocketAddress remoteAddress;
  public final HttpMethod method;
  public final String uri;
  public final MultiMap headers;
  public final HostAndPort authority;
  public final String absoluteURI;
  public final String traceOperation;

  public HttpRequestHead(HttpMethod method, String uri, MultiMap headers, HostAndPort authority, String absoluteURI, String traceOperation) {
    if (uri == null || uri.isEmpty()) {
      throw new IllegalArgumentException("Invalid request URI");
    }
    this.method = method;
    this.uri = uri;
    this.headers = headers;
    this.authority = authority;
    this.absoluteURI = absoluteURI;
    this.traceOperation = traceOperation;
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public SocketAddress remoteAddress() {
    return remoteAddress;
  }

  @Override
  public String absoluteURI() {
    return absoluteURI;
  }

  @Override
  public int id() {
    return 1;
  }

  @Override
  public String uri() {
    return uri;
  }

  @Override
  public HttpMethod method() {
    return method;
  }
}
