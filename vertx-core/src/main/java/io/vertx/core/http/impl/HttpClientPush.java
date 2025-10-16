/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
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
public class HttpClientPush implements HttpRequest {

  private final String uri;
  private final HttpMethod method;
  private final HostAndPort authority;
  private final HttpClientStream stream;
  private final MultiMap headers;

  public HttpClientPush(HttpRequestHead head, HttpClientStream stream) {

    String rawMethod = head.method().toString();
    String authority = head.authority != null ? head.authority.toString() : null;
    int pos = authority == null ? -1 : authority.indexOf(':');
    if (pos == -1) {
      this.authority = HostAndPort.create(authority, 80);
    } else {
      this.authority = HostAndPort.create(authority.substring(0, pos), Integer.parseInt(authority.substring(pos + 1)));
    }
    this.method = HttpMethod.valueOf(rawMethod);
    this.uri = head.uri;
    this.stream = stream;
    this.headers = head.headers;
  }

  public HttpClientStream stream() {
    return stream;
  }

  @Override
  public int id() {
    return stream.id();
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public String absoluteURI() {
    return null;
  }

  @Override
  public SocketAddress remoteAddress() {
    return stream.connection().remoteAddress();
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
