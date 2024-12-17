/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.http;

import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.impl.headers.HeadersAdaptor;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.impl.SysProps;

/**
 * HTTP multimap implementations.
 */
public interface HttpHeadersInternal extends HttpHeaders {

  /** JVM system property that disables HTTP headers validation, don't use this in production. */
  @Deprecated
  String DISABLE_HTTP_HEADERS_VALIDATION_PROP_NAME = SysProps.DISABLE_HTTP_HEADERS_VALIDATION.name;

  /** Constant that disables HTTP headers validation, this is a constant so the JIT can eliminate validation code. */
  @Deprecated
  boolean DISABLE_HTTP_HEADERS_VALIDATION = SysProps.DISABLE_HTTP_HEADERS_VALIDATION.getBoolean();

  /**
   * @return a multimap wrapping Netty HTTP {code header} instance
   */
  static MultiMap headers(io.netty.handler.codec.http.HttpHeaders headers) {
    return new HeadersAdaptor(headers);
  }

  /**
   * @return a multimap wrapping Netty HTTP/2 {code header} instance
   */
  static MultiMap headers(Http2Headers headers) {
    return new Http2HeadersAdaptor(headers);
  }
}
