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

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class HttpResponseHeaders extends HttpHeaders {

  private Integer status;

  public HttpResponseHeaders(Headers<CharSequence, CharSequence, ?> headers) {
    this(true, headers);
  }

  HttpResponseHeaders(boolean mutable, Headers<CharSequence, CharSequence, ?> headers) {
    super(mutable, headers);
  }

  public HttpHeaders status(CharSequence status) {
    if (status != null) {
      headers.set(io.vertx.core.http.HttpHeaders.PSEUDO_STATUS, status);
    } else {
      headers.remove(io.vertx.core.http.HttpHeaders.PSEUDO_STATUS);
    }
    return this;
  }

  public Integer status() {
    return status;
  }

  public HttpHeaders status(Integer status) {
    this.status = status;
    return this;
  }

  public boolean validate() {
    CharSequence statusHeader = headers.get(io.vertx.core.http.HttpHeaders.PSEUDO_STATUS);
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

  public HttpHeaders sanitize() {
    headers.remove(io.vertx.core.http.HttpHeaders.PSEUDO_STATUS);
    return this;
  }

  public void prepare() {
    if (status != null) {
      headers.set(io.vertx.core.http.HttpHeaders.PSEUDO_STATUS, status.toString());
    }
  }

  @Override
  HttpHeaders copy(boolean mutable, Headers<CharSequence, CharSequence, ?> headers) {
    return new HttpResponseHeaders(mutable, new DefaultHttp2Headers().setAll(headers));
  }
}
