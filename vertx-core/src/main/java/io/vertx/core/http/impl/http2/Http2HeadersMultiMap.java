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
package io.vertx.core.http.impl.http2;

import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.headers.HttpHeadersAdaptor;
import io.vertx.core.net.HostAndPort;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http2HeadersMultiMap extends HttpHeadersAdaptor<Http2Headers> {

  public Http2HeadersMultiMap() {
    this(new DefaultHttp2Headers());
  }

  public Http2HeadersMultiMap(Http2HeadersMultiMap http2HeadersAdaptor) {
    this(http2HeadersAdaptor.headers);
  }

  public Http2HeadersMultiMap(Http2Headers headers) {
    super(headers);
  }

  public Http2HeadersMultiMap(boolean mutable, Http2Headers headers) {
    super(mutable, headers);
  }

  @Override
  protected boolean containsHeader(CharSequence name, CharSequence value, boolean caseInsensitive) {
    return headers.contains(name, value, caseInsensitive);
  }

  @Override
  public Http2HeadersMultiMap status(CharSequence status) {
    super.status(status);
    return this;
  }

  @Override
  public Http2HeadersMultiMap status(Integer status) {
    super.status(status);
    return this;
  }

  @Override
  public Http2HeadersMultiMap path(String path) {
    super.path(path);
    return this;
  }

  public Http2HeadersMultiMap method(HttpMethod method) {
    super.method(method);
    return this;
  }

  public Http2HeadersMultiMap authority(HostAndPort authority) {
    super.authority(authority);
    return this;
  }

  public Http2HeadersMultiMap scheme(String scheme) {
    super.scheme(scheme);
    return this;
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
    return new Http2HeadersMultiMap(mutable, new DefaultHttp2Headers().setAll(headers));
  }
}
