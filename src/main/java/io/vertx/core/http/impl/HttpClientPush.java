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

import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.spi.metrics.HttpClientMetrics;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpClientPush implements HttpClientMetrics.Request {

  final Http2Headers headers;
  final HttpClientStream stream;

  public HttpClientPush(Http2Headers headers, HttpClientStream stream) {
    this.headers = headers;
    this.stream = stream;
  }

  @Override
  public int id() {
    return stream.id();
  }

  @Override
  public String uri() {
    return headers.path().toString();
  }

  @Override
  public HttpMethod method() {
    String rawMethod = headers.method().toString();
    HttpMethod method = HttpMethod.valueOf(rawMethod);
    return method;
  }
}
