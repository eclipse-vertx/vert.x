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

import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.core.MultiMap;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http2HeadersAdaptor extends HttpHeadersAdaptor<Http2Headers> {

  public Http2HeadersAdaptor() {
    this(new DefaultHttp2Headers());
  }

  public Http2HeadersAdaptor(Http2HeadersAdaptor http2HeadersAdaptor) {
    this(http2HeadersAdaptor.headers);
  }

  public Http2HeadersAdaptor(Http2Headers headers) {
    super(headers);
  }

  public Http2HeadersAdaptor(boolean mutable, Http2Headers headers) {
    super(mutable, headers);
  }

  @Override
  protected boolean containsHeader(CharSequence name, CharSequence value, boolean caseInsensitive) {
    return headers.contains(name, value, caseInsensitive);
  }

  @Override
  public void method(CharSequence value) {
    this.headers.method(value);
  }

  @Override
  public void authority(CharSequence authority) {
    this.headers.authority(authority);
  }

  @Override
  public CharSequence authority() {
    return this.headers.authority();
  }

  @Override
  public void path(CharSequence value) {
    this.headers.path(value);
  }

  @Override
  public void scheme(CharSequence value) {
    this.headers.scheme(value);
  }

  @Override
  public CharSequence path() {
    return this.headers.path();
  }

  @Override
  public CharSequence method() {
    return this.headers.method();
  }

  @Override
  public CharSequence status() {
    return this.headers.status();
  }

  @Override
  public void status(CharSequence status) {
    this.headers.status(status);
  }

  @Override
  public CharSequence scheme() {
    return this.headers.scheme();
  }

  @Override
  public boolean contains(CharSequence name, CharSequence value) {
    return headers.contains(name, value);
  }

  @Override
  public Http2Headers getHeaders() {
    return headers;
  }

  @Override
  public MultiMap copy(boolean mutable) {
    if (!isMutable() && ! mutable) {
      return this;
    }
    return new Http2HeadersAdaptor(mutable, new DefaultHttp2Headers().setAll(headers));
  }
}
