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

package io.vertx.core.http;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;

import java.util.Map;

/**
 * Holds HTTP headers.
 *
 * In addition, this class declares a bunch of useful HTTP headers stuff:
 *
 * <ul>
 *   <li>methods for creating {@link MultiMap} instances</li>
 *   <li>often used Header names</li>
 *   <li>method to create optimized {@link CharSequence} which can be used as header name and value</li>
 * </ul>
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@DataObject
public interface HttpHeaders extends MultiMap {

  /**
   * Accept header name
   */
  CharSequence ACCEPT = HttpHeaderNames.ACCEPT;

  /**
   * Accept-Charset header name
   */
  CharSequence ACCEPT_CHARSET = HttpHeaderNames.ACCEPT_CHARSET;

  /**
   * Accept-Encoding header name
   */
  CharSequence ACCEPT_ENCODING = HttpHeaderNames.ACCEPT_ENCODING;

  /**
   * Accept-Language header name
   */
  CharSequence ACCEPT_LANGUAGE = HttpHeaderNames.ACCEPT_LANGUAGE;

  /**
   * Accept-Ranges header name
   */
  CharSequence ACCEPT_RANGES = HttpHeaderNames.ACCEPT_RANGES;

  /**
   * Accept-Patch header name
   */
  CharSequence ACCEPT_PATCH = HttpHeaderNames.ACCEPT_PATCH;

  /**
   * Access-Control-Allow-Credentials header name
   */
  CharSequence ACCESS_CONTROL_ALLOW_CREDENTIALS = HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS;

  /**
   * Access-Control-Allow-Headers header name
   */
  CharSequence ACCESS_CONTROL_ALLOW_HEADERS = HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS;

  /**
   * Access-Control-Allow-Methods header name
   */
  CharSequence ACCESS_CONTROL_ALLOW_METHODS = HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS;

  /**
   * Access-Control-Allow-Origin header name
   */
  CharSequence ACCESS_CONTROL_ALLOW_ORIGIN = HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN;

  /**
   * Access-Control-Allow-Private-Network header name
   */
  CharSequence ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK = HttpHeaderNames.ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK;

  /**
   * Access-Control-Expose-Headers header name
   */
  CharSequence ACCESS_CONTROL_EXPOSE_HEADERS = HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS;

  /**
   * Access-Control-Max-Age header name
   */
  CharSequence ACCESS_CONTROL_MAX_AGE = HttpHeaderNames.ACCESS_CONTROL_MAX_AGE;

  /**
   * Access-Control-Request-Headers header name
   */
  CharSequence ACCESS_CONTROL_REQUEST_HEADERS = HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS;

  /**
   * Access-Control-Request-Method header name
   */
  CharSequence ACCESS_CONTROL_REQUEST_METHOD = HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD;

  /**
   * Access-Control-Request-Private-Network header name
   */
  CharSequence ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK = HttpHeaderNames.ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK;

  /**
   *  Age header name
   */
  CharSequence AGE = HttpHeaderNames.AGE;

  /**
   * Allow header name
   */
  CharSequence ALLOW = HttpHeaderNames.ALLOW;

  /**
   * Authorization header name
   */
  CharSequence AUTHORIZATION = HttpHeaderNames.AUTHORIZATION;

  /**
   * Cache-Control header name
   */
  CharSequence CACHE_CONTROL = HttpHeaderNames.CACHE_CONTROL;

  /**
   * Connection header name
   */
  CharSequence CONNECTION = HttpHeaderNames.CONNECTION;

  /**
   * Content-Base header name
   */
  CharSequence CONTENT_BASE = HttpHeaderNames.CONTENT_BASE;

  /**
   * Content-Disposition header name
   */
  CharSequence CONTENT_DISPOSITION = HttpHeaderNames.CONTENT_DISPOSITION;

  /**
   * Content-Encoding header name
   */
  CharSequence CONTENT_ENCODING = HttpHeaderNames.CONTENT_ENCODING;

  /**
   * Content-Language header name
   */
  CharSequence CONTENT_LANGUAGE = HttpHeaderNames.CONTENT_LANGUAGE;

  /**
   * Content-Length header name
   */
  CharSequence CONTENT_LENGTH = HttpHeaderNames.CONTENT_LENGTH;

  /**
   * Content-Location header name
   */
  CharSequence CONTENT_LOCATION = HttpHeaderNames.CONTENT_LOCATION;

  /**
   * Content-Transfer-Encoding header name
   */
  CharSequence CONTENT_TRANSFER_ENCODING = HttpHeaderNames.CONTENT_TRANSFER_ENCODING;

  /**
   * Content-MD5 header name
   */
  CharSequence CONTENT_MD5 = HttpHeaderNames.CONTENT_MD5;

  /**
   * Content-Rage header name
   */
  CharSequence CONTENT_RANGE = HttpHeaderNames.CONTENT_RANGE;

  /**
   * Content-Type header name
   */
  CharSequence CONTENT_TYPE = HttpHeaderNames.CONTENT_TYPE;

  /**
   * Content-Cookie header name
   */
  CharSequence COOKIE = HttpHeaderNames.COOKIE;

  /**
   * Date header name
   */
  CharSequence DATE = HttpHeaderNames.DATE;

  /**
   * Etag header name
   */
  CharSequence ETAG = HttpHeaderNames.ETAG;

  /**
   * Expect header name
   */
  CharSequence EXPECT = HttpHeaderNames.EXPECT;

  /**
   * Expires header name
   */
  CharSequence EXPIRES = HttpHeaderNames.EXPIRES;

  /**
   * From header name
   */
  CharSequence FROM = HttpHeaderNames.FROM;

  /**
   * Host header name
   */
  CharSequence HOST = HttpHeaderNames.HOST;

  /**
   * If-Match header name
   */
  CharSequence IF_MATCH = HttpHeaderNames.IF_MATCH;

  /**
   * If-Modified-Since header name
   */
  CharSequence IF_MODIFIED_SINCE = HttpHeaderNames.IF_MODIFIED_SINCE;

  /**
   * If-None-Match header name
   */
  CharSequence IF_NONE_MATCH = HttpHeaderNames.IF_NONE_MATCH;

  /**
   * Last-Modified header name
   */
  CharSequence LAST_MODIFIED = HttpHeaderNames.LAST_MODIFIED;

  /**
   * Location header name
   */
  CharSequence LOCATION = HttpHeaderNames.LOCATION;

  /**
   * Origin header name
   */
  CharSequence ORIGIN = HttpHeaderNames.ORIGIN;

  /**
   * Proxy-Authenticate header name
   */
  CharSequence PROXY_AUTHENTICATE = HttpHeaderNames.PROXY_AUTHENTICATE;

  /**
   * Proxy-Authorization header name
   */
  CharSequence PROXY_AUTHORIZATION = HttpHeaderNames.PROXY_AUTHORIZATION;

  /**
   * Referer header name
   */
  CharSequence REFERER = HttpHeaderNames.REFERER;

  /**
   * Retry-After header name
   */
  CharSequence RETRY_AFTER = HttpHeaderNames.RETRY_AFTER;

  /**
   * Server header name
   */
  CharSequence SERVER = HttpHeaderNames.SERVER;

  /**
   * Transfer-Encoding header name
   */
  CharSequence TRANSFER_ENCODING = HttpHeaderNames.TRANSFER_ENCODING;

  /**
   * User-Agent header name
   */
  CharSequence USER_AGENT = HttpHeaderNames.USER_AGENT;

  /**
   * Set-Cookie header name
   */
  CharSequence SET_COOKIE = HttpHeaderNames.SET_COOKIE;

  /**
   * application/x-www-form-urlencoded header value
   */
  CharSequence APPLICATION_X_WWW_FORM_URLENCODED = HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED;

  /**
   * multipart/form-data header value
   */
  CharSequence MULTIPART_FORM_DATA = HttpHeaderValues.MULTIPART_FORM_DATA;

  /**
   * chunked header value
   */
  CharSequence CHUNKED = HttpHeaderValues.CHUNKED;

  /**
   * close header value
   */
  CharSequence CLOSE = HttpHeaderValues.CLOSE;

  /**
   * 100-continue header value
   */
  CharSequence CONTINUE = HttpHeaderValues.CONTINUE;

  /**
   * identity header value
   */
  CharSequence IDENTITY = HttpHeaderValues.IDENTITY;

  /**
   * keep-alive header value
   */
  CharSequence KEEP_ALIVE = HttpHeaderValues.KEEP_ALIVE;

  /**
   * Upgrade header value
   */
  CharSequence UPGRADE = HttpHeaderValues.UPGRADE;

  /**
   * WebSocket header value
   */
  CharSequence WEBSOCKET = HttpHeaderValues.WEBSOCKET;

  /**
   * deflate,gzip header value
   */
  CharSequence DEFLATE_GZIP = createOptimized("deflate, gzip");

  /**
   * deflate,gzip,br header value
   */
  CharSequence DEFLATE_GZIP_BR = createOptimized("deflate, gzip, br");

  /**
   * deflate,gzip,zstd,br header value
   */
  CharSequence DEFLATE_GZIP_ZSTD_BR_SNAPPY = createOptimized("deflate, gzip, zstd, br, snappy");

  /**
   * deflate,gzip,zstd header value
   */
  CharSequence DEFLATE_GZIP_ZSTD = createOptimized("deflate, gzip, zstd");

  /**
   * text/html header value
   */
  CharSequence TEXT_HTML = createOptimized("text/html");

  /**
   * GET header value
   */
  CharSequence GET = createOptimized("GET");

  /**
   * Vary header name
   */
  CharSequence VARY = createOptimized("vary");

  /**
   * HTTP/2 {@code :path} pseudo header
   */
  CharSequence PSEUDO_PATH = Http2Headers.PseudoHeaderName.PATH.value();

  /**
   * HTTP/2 {@code :authority} pseudo header
   */
  CharSequence PSEUDO_AUTHORITY = Http2Headers.PseudoHeaderName.AUTHORITY.value();

  /**
   * HTTP/2 {@code :scheme} pseudo header
   */
  CharSequence PSEUDO_SCHEME = Http2Headers.PseudoHeaderName.SCHEME.value();

  /**
   * HTTP/2 {@code :status} pseudo header
   */
  CharSequence PSEUDO_STATUS = Http2Headers.PseudoHeaderName.STATUS.value();

  /**
   * HTTP/2 {@code :method} pseudo hedaer
   */
  CharSequence PSEUDO_METHOD = Http2Headers.PseudoHeaderName.METHOD.value();

  /**
   * Create an optimized {@link CharSequence} which can be used as header name or value.
   * This should be used if you expect to use it multiple times liked for example adding the same header name or value
   * for multiple responses or requests.
   */
  static CharSequence createOptimized(String value) {
    return new AsciiString(value);
  }

  /**
   * @return a {@link MultiMap} backing an HTTP headers structure optimized for {@code HTTP/1.x}
   */
  static HttpHeaders headers() {
    return HeadersMultiMap.httpHeaders();
  }

  /**
   * @param version version HTTP protocol hint for which the returned instance is optimized for
   * @return a {@link MultiMap} backing an HTTP headers structure optimized for the {@code version} hint
   */
  static HttpHeaders headers(HttpVersion version) {
    switch (version) {
      case HTTP_1_0:
      case HTTP_1_1:
        return HeadersMultiMap.httpHeaders();
      case HTTP_2:
        return new Http2HeadersAdaptor(new DefaultHttp2Headers());
      default:
        throw new AssertionError();
    }
  }

  /**
   * @return whether this instance can be mutated.
   */
  boolean isMutable();

  /**
   * Returns a copy of this instance.
   *
   * @param mutable whether the copy can be mutated
   * @return a copy of this instance
   */
  HttpHeaders copy(boolean mutable);

  /**
   * Returns a mutable copy of this instance.
   *
   * @return a mutable copy of this instance
   */
  default HttpHeaders copy() {
    return copy(true);
  }

  @Override
  HttpHeaders add(String name, String value);

  @Override
  HttpHeaders add(CharSequence name, CharSequence value);

  @Override
  HttpHeaders add(String name, Iterable<String> values);

  @Override
  HttpHeaders add(CharSequence name, Iterable<CharSequence> values);

  @Override
  HttpHeaders addAll(MultiMap map);

  @Override
  HttpHeaders addAll(Map<String, String> headers);

  @Override
  HttpHeaders set(String name, String value);

  @Override
  HttpHeaders set(CharSequence name, CharSequence value);

  @Override
  HttpHeaders set(String name, Iterable<String> values);

  @Override
  HttpHeaders set(CharSequence name, Iterable<CharSequence> values);

  @Override
  HttpHeaders setAll(MultiMap map);

  @Override
  HttpHeaders setAll(Map<String, String> headers);

  @Override
  HttpHeaders remove(String name);

  @Override
  HttpHeaders remove(CharSequence name);

  @Override
  HttpHeaders clear();
}
