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
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.headers.HeadersMultiMap;

/**
 * Contains a bunch of useful HTTP headers stuff:
 *
 * <ul>
 *   <li>methods for creating {@link MultiMap} instances</li>
 *   <li>often used Header names</li>
 *   <li>method to create optimized {@link CharSequence} which can be used as header name and value</li>
 * </ul>
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@VertxGen
public interface HttpHeaders {

  /**
   * Accept header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ACCEPT = HttpHeaderNames.ACCEPT;

  /**
   * Accept-Charset header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ACCEPT_CHARSET = HttpHeaderNames.ACCEPT_CHARSET;

  /**
   * Accept-Encoding header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ACCEPT_ENCODING = HttpHeaderNames.ACCEPT_ENCODING;

  /**
   * Accept-Language header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ACCEPT_LANGUAGE = HttpHeaderNames.ACCEPT_LANGUAGE;

  /**
   * Accept-Ranges header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ACCEPT_RANGES = HttpHeaderNames.ACCEPT_RANGES;

  /**
   * Accept-Patch header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ACCEPT_PATCH = HttpHeaderNames.ACCEPT_PATCH;

  /**
   * Access-Control-Allow-Credentials header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ACCESS_CONTROL_ALLOW_CREDENTIALS = HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS;

  /**
   * Access-Control-Allow-Headers header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ACCESS_CONTROL_ALLOW_HEADERS = HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS;

  /**
   * Access-Control-Allow-Methods header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ACCESS_CONTROL_ALLOW_METHODS = HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS;

  /**
   * Access-Control-Allow-Origin header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ACCESS_CONTROL_ALLOW_ORIGIN = HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN;

  /**
   * Access-Control-Allow-Private-Network header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK = HttpHeaderNames.ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK;

  /**
   * Access-Control-Expose-Headers header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ACCESS_CONTROL_EXPOSE_HEADERS = HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS;

  /**
   * Access-Control-Max-Age header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ACCESS_CONTROL_MAX_AGE = HttpHeaderNames.ACCESS_CONTROL_MAX_AGE;

  /**
   * Access-Control-Request-Headers header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ACCESS_CONTROL_REQUEST_HEADERS = HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS;

  /**
   * Access-Control-Request-Method header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ACCESS_CONTROL_REQUEST_METHOD = HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD;

  /**
   * Access-Control-Request-Private-Network header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK = HttpHeaderNames.ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK;

  /**
   *  Age header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence AGE = HttpHeaderNames.AGE;

  /**
   * Allow header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ALLOW = HttpHeaderNames.ALLOW;

  /**
   * Authorization header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence AUTHORIZATION = HttpHeaderNames.AUTHORIZATION;

  /**
   * Cache-Control header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence CACHE_CONTROL = HttpHeaderNames.CACHE_CONTROL;

  /**
   * Connection header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence CONNECTION = HttpHeaderNames.CONNECTION;

  /**
   * Content-Base header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence CONTENT_BASE = HttpHeaderNames.CONTENT_BASE;

  /**
   * Content-Disposition header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence CONTENT_DISPOSITION = HttpHeaderNames.CONTENT_DISPOSITION;

  /**
   * Content-Encoding header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence CONTENT_ENCODING = HttpHeaderNames.CONTENT_ENCODING;

  /**
   * Content-Language header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence CONTENT_LANGUAGE = HttpHeaderNames.CONTENT_LANGUAGE;

  /**
   * Content-Length header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence CONTENT_LENGTH = HttpHeaderNames.CONTENT_LENGTH;

  /**
   * Content-Location header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence CONTENT_LOCATION = HttpHeaderNames.CONTENT_LOCATION;

  /**
   * Content-Transfer-Encoding header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence CONTENT_TRANSFER_ENCODING = HttpHeaderNames.CONTENT_TRANSFER_ENCODING;

  /**
   * Content-MD5 header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence CONTENT_MD5 = HttpHeaderNames.CONTENT_MD5;

  /**
   * Content-Rage header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence CONTENT_RANGE = HttpHeaderNames.CONTENT_RANGE;

  /**
   * Content-Type header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence CONTENT_TYPE = HttpHeaderNames.CONTENT_TYPE;

  /**
   * Content-Cookie header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence COOKIE = HttpHeaderNames.COOKIE;

  /**
   * Date header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence DATE = HttpHeaderNames.DATE;

  /**
   * Etag header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ETAG = HttpHeaderNames.ETAG;

  /**
   * Expect header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence EXPECT = HttpHeaderNames.EXPECT;

  /**
   * Expires header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence EXPIRES = HttpHeaderNames.EXPIRES;

  /**
   * From header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence FROM = HttpHeaderNames.FROM;

  /**
   * Host header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence HOST = HttpHeaderNames.HOST;

  /**
   * If-Match header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence IF_MATCH = HttpHeaderNames.IF_MATCH;

  /**
   * If-Modified-Since header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence IF_MODIFIED_SINCE = HttpHeaderNames.IF_MODIFIED_SINCE;

  /**
   * If-None-Match header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence IF_NONE_MATCH = HttpHeaderNames.IF_NONE_MATCH;

  /**
   * Last-Modified header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence LAST_MODIFIED = HttpHeaderNames.LAST_MODIFIED;

  /**
   * Location header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence LOCATION = HttpHeaderNames.LOCATION;

  /**
   * Origin header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence ORIGIN = HttpHeaderNames.ORIGIN;

  /**
   * Proxy-Authenticate header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence PROXY_AUTHENTICATE = HttpHeaderNames.PROXY_AUTHENTICATE;

  /**
   * Proxy-Authorization header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence PROXY_AUTHORIZATION = HttpHeaderNames.PROXY_AUTHORIZATION;

  /**
   * Referer header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence REFERER = HttpHeaderNames.REFERER;

  /**
   * Retry-After header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence RETRY_AFTER = HttpHeaderNames.RETRY_AFTER;

  /**
   * Server header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence SERVER = HttpHeaderNames.SERVER;

  /**
   * Transfer-Encoding header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence TRANSFER_ENCODING = HttpHeaderNames.TRANSFER_ENCODING;

  /**
   * User-Agent header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence USER_AGENT = HttpHeaderNames.USER_AGENT;

  /**
   * Set-Cookie header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence SET_COOKIE = HttpHeaderNames.SET_COOKIE;

  /**
   * application/x-www-form-urlencoded header value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence APPLICATION_X_WWW_FORM_URLENCODED = HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED;

  /**
   * multipart/form-data header value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence MULTIPART_FORM_DATA = HttpHeaderValues.MULTIPART_FORM_DATA;

  /**
   * chunked header value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence CHUNKED = HttpHeaderValues.CHUNKED;
  /**
   * close header value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence CLOSE = HttpHeaderValues.CLOSE;

  /**
   * 100-continue header value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence CONTINUE = HttpHeaderValues.CONTINUE;

  /**
   * identity header value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence IDENTITY = HttpHeaderValues.IDENTITY;
  /**
   * keep-alive header value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence KEEP_ALIVE = HttpHeaderValues.KEEP_ALIVE;

  /**
   * Upgrade header value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence UPGRADE = HttpHeaderValues.UPGRADE;

  /**
   * WebSocket header value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence WEBSOCKET = HttpHeaderValues.WEBSOCKET;

  /**
   * deflate,gzip header value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence DEFLATE_GZIP = createOptimized("deflate, gzip");

  /**
   * deflate,gzip,br header value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence DEFLATE_GZIP_BR = createOptimized("deflate, gzip, br");

  /**
   * deflate,gzip,zstd,br header value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence DEFLATE_GZIP_ZSTD_BR_SNAPPY = createOptimized("deflate, gzip, zstd, br, snappy");

  /**
   * deflate,gzip,zstd header value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence DEFLATE_GZIP_ZSTD = createOptimized("deflate, gzip, zstd");

  /**
   * text/html header value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence TEXT_HTML = createOptimized("text/html");

  /**
   * GET header value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence GET = createOptimized("GET");

  /**
   * Vary header name
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence VARY = createOptimized("vary");

  /**
   * HTTP/2 {@code :path} pseudo header
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence PSEUDO_PATH = Http2Headers.PseudoHeaderName.PATH.value();

  /**
   * HTTP/2 {@code :authority} pseudo header
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence PSEUDO_AUTHORITY = Http2Headers.PseudoHeaderName.AUTHORITY.value();

  /**
   * HTTP/2 {@code :scheme} pseudo header
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence PSEUDO_SCHEME = Http2Headers.PseudoHeaderName.SCHEME.value();

  /**
   * HTTP/2 {@code :status} pseudo header
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence PSEUDO_STATUS = Http2Headers.PseudoHeaderName.STATUS.value();

  /**
   * HTTP/2 {@code :method} pseudo hedaer
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  CharSequence PSEUDO_METHOD = Http2Headers.PseudoHeaderName.METHOD.value();

  /**
   * Create an optimized {@link CharSequence} which can be used as header name or value.
   * This should be used if you expect to use it multiple times liked for example adding the same header name or value
   * for multiple responses or requests.
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  static CharSequence createOptimized(String value) {
    return new AsciiString(value);
  }

  static MultiMap headers() {
    return HeadersMultiMap.httpHeaders();
  }

  static MultiMap set(String name, String value) {
    return HeadersMultiMap.httpHeaders().set(name, value);
  }

  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  static MultiMap set(CharSequence name, CharSequence value) {
    return HeadersMultiMap.httpHeaders().set(name, value);
  }
}
