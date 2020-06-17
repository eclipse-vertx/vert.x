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

import io.netty.handler.codec.http.*;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.impl.headers.HeadersMultiMap;

/**
 * A request decoder using {@link HeadersMultiMap} which is faster than {@code DefaultHttpHeaders} used by the super class.
 */
public class VertxHttpRequestDecoder extends HttpRequestDecoder {

  public VertxHttpRequestDecoder(HttpServerOptions options) {
    super(
      options.getMaxInitialLineLength(),
      options.getMaxHeaderSize(),
      options.getMaxChunkSize(),
      !HttpHeaders.DISABLE_HTTP_HEADERS_VALIDATION,
      options.getDecoderInitialBufferSize());
  }

  @Override
  protected HttpMessage createMessage(String[] initialLine) {
    return new DefaultHttpRequest(
      HttpVersion.valueOf(initialLine[2]),
      HttpMethod.valueOf(initialLine[0]),
      initialLine[1],
      HeadersMultiMap.httpHeaders());
  }
}
