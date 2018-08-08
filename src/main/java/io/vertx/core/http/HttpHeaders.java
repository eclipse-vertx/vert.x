/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.netty.util.AsciiString;
import io.vertx.core.http.impl.HttpUtils;

/**
 * Contains often used Header names.
 * <p>
 * It also contains a utility method to create optimized {@link CharSequence} which can be used as header name and value.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class HttpHeaders {


  /**
   * Accept header name
   */
  public static final CharSequence ACCEPT = createOptimized(HttpHeaderNames.ACCEPT.toString());

  /**
   * Accept-Charset header name
   */
  public static final CharSequence ACCEPT_CHARSET = createOptimized(HttpHeaderNames.ACCEPT_CHARSET.toString());

  /**
   * Accept-Encoding header name
   */
  public static final CharSequence ACCEPT_ENCODING = createOptimized(HttpHeaderNames.ACCEPT_ENCODING.toString());

  /**
   * Accept-Language header name
   */
  public static final CharSequence ACCEPT_LANGUAGE = createOptimized(HttpHeaderNames.ACCEPT_LANGUAGE.toString());

  /**
   * Accept-Ranges header name
   */
  public static final CharSequence ACCEPT_RANGES = createOptimized(HttpHeaderNames.ACCEPT_RANGES.toString());

  /**
   * Accept-Patch header name
   */
  public static final CharSequence ACCEPT_PATCH = createOptimized(HttpHeaderNames.ACCEPT_PATCH.toString());

  /**
   * Access-Control-Allow-Credentials header name
   */
  public static final CharSequence ACCESS_CONTROL_ALLOW_CREDENTIALS = createOptimized(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString());

  /**
   * Access-Control-Allow-Headers header name
   */
  public static final CharSequence ACCESS_CONTROL_ALLOW_HEADERS = createOptimized(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS.toString());

  /**
   * Access-Control-Allow-Methods header name
   */
  public static final CharSequence ACCESS_CONTROL_ALLOW_METHODS = createOptimized(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS.toString());

  /**
   * Access-Control-Allow-Origin header name
   */
  public static final CharSequence ACCESS_CONTROL_ALLOW_ORIGIN = createOptimized(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString());

  /**
   * Access-Control-Expose-Headers header name
   */
  public static final CharSequence ACCESS_CONTROL_EXPOSE_HEADERS = createOptimized(HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS.toString());

  /**
   * Access-Control-Max-Age header name
   */
  public static final CharSequence ACCESS_CONTROL_MAX_AGE = createOptimized(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE.toString());

  /**
   * Access-Control-Request-Headers header name
   */
  public static final CharSequence ACCESS_CONTROL_REQUEST_HEADERS = createOptimized(HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS.toString());

  /**
   * Access-Control-Request-Method header name
   */
  public static final CharSequence ACCESS_CONTROL_REQUEST_METHOD = createOptimized(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD.toString());

  /**
   *  Age header name
   */
  public static final CharSequence AGE = createOptimized(HttpHeaderNames.AGE.toString());

  /**
   * Allow header name
   */
  public static final CharSequence ALLOW = createOptimized(HttpHeaderNames.ALLOW.toString());

  /**
   * Authorization header name
   */
  public static final CharSequence AUTHORIZATION = createOptimized(HttpHeaderNames.AUTHORIZATION.toString());

  /**
   * Cache-Control header name
   */
  public static final CharSequence CACHE_CONTROL = createOptimized(HttpHeaderNames.CACHE_CONTROL.toString());

  /**
   * Connection header name
   */
  public static final CharSequence CONNECTION = createOptimized(HttpHeaderNames.CONNECTION.toString());

  /**
   * Content-Base header name
   */
  public static final CharSequence CONTENT_BASE = createOptimized(HttpHeaderNames.CONTENT_BASE.toString());

  /**
   * Content-Disposition header name
   */
  public static final CharSequence CONTENT_DISPOSITION = createOptimized(HttpHeaderNames.CONTENT_DISPOSITION.toString());
  
  /**
   * Content-Encoding header name
   */
  public static final CharSequence CONTENT_ENCODING = createOptimized(HttpHeaderNames.CONTENT_ENCODING.toString());

  /**
   * Content-Language header name
   */
  public static final CharSequence CONTENT_LANGUAGE = createOptimized(HttpHeaderNames.CONTENT_LANGUAGE.toString());

  /**
   * Content-Length header name
   */
  public static final CharSequence CONTENT_LENGTH = createOptimized(HttpHeaderNames.CONTENT_LENGTH.toString());

  /**
   * Content-Location header name
   */
  public static final CharSequence CONTENT_LOCATION = createOptimized(HttpHeaderNames.CONTENT_LOCATION.toString());

  /**
   * Content-Transfer-Encoding header name
   */
  public static final CharSequence CONTENT_TRANSFER_ENCODING = createOptimized(HttpHeaderNames.CONTENT_TRANSFER_ENCODING.toString());

  /**
   * Content-MD5 header name
   */
  public static final CharSequence CONTENT_MD5 = createOptimized(HttpHeaderNames.CONTENT_MD5.toString());

  /**
   * Content-Rage header name
   */
  public static final CharSequence CONTENT_RANGE = createOptimized(HttpHeaderNames.CONTENT_RANGE.toString());

  /**
   * Content-Type header name
   */
  public static final CharSequence CONTENT_TYPE = createOptimized(HttpHeaderNames.CONTENT_TYPE.toString());

  /**
   * Content-Cookie header name
   */
  public static final CharSequence COOKIE = createOptimized(HttpHeaderNames.COOKIE.toString());

  /**
   * Date header name
   */
  public static final CharSequence DATE = createOptimized(HttpHeaderNames.DATE.toString());

  /**
   * Etag header name
   */
  public static final CharSequence ETAG = createOptimized(HttpHeaderNames.ETAG.toString());

  /**
   * Expect header name
   */
  public static final CharSequence EXPECT = createOptimized(HttpHeaderNames.EXPECT.toString());

  /**
   * Expires header name
   */
  public static final CharSequence EXPIRES = createOptimized(HttpHeaderNames.EXPIRES.toString());

  /**
   * From header name
   */
  public static final CharSequence FROM = createOptimized(HttpHeaderNames.FROM.toString());

  /**
   * Host header name
   */
  public static final CharSequence HOST = createOptimized(HttpHeaderNames.HOST.toString());

  /**
   * If-Match header name
   */
  public static final CharSequence IF_MATCH = createOptimized(HttpHeaderNames.IF_MATCH.toString());

  /**
   * If-Modified-Since header name
   */
  public static final CharSequence IF_MODIFIED_SINCE = createOptimized(HttpHeaderNames.IF_MODIFIED_SINCE.toString());

  /**
   * If-None-Match header name
   */
  public static final CharSequence IF_NONE_MATCH = createOptimized(HttpHeaderNames.IF_NONE_MATCH.toString());

  /**
   * Last-Modified header name
   */
  public static final CharSequence LAST_MODIFIED = createOptimized(HttpHeaderNames.LAST_MODIFIED.toString());

  /**
   * Location header name
   */
  public static final CharSequence LOCATION = createOptimized(HttpHeaderNames.LOCATION.toString());

  /**
   * Origin header name
   */
  public static final CharSequence ORIGIN = createOptimized(HttpHeaderNames.ORIGIN.toString());

  /**
   * Proxy-Authenticate header name
   */
  public static final CharSequence PROXY_AUTHENTICATE = createOptimized(HttpHeaderNames.PROXY_AUTHENTICATE.toString());

  /**
   * Proxy-Authorization header name
   */
  public static final CharSequence PROXY_AUTHORIZATION = createOptimized(HttpHeaderNames.PROXY_AUTHORIZATION.toString());

  /**
   * Referer header name
   */
  public static final CharSequence REFERER = createOptimized(HttpHeaderNames.REFERER.toString());

  /**
   * Retry-After header name
   */
  public static final CharSequence RETRY_AFTER = createOptimized(HttpHeaderNames.RETRY_AFTER.toString());

  /**
   * Server header name
   */
  public static final CharSequence SERVER = createOptimized(HttpHeaderNames.SERVER.toString());

  /**
   * Transfer-Encoding header name
   */
  public static final CharSequence TRANSFER_ENCODING = createOptimized(HttpHeaderNames.TRANSFER_ENCODING.toString());

  /**
   * User-Agent header name
   */
  public static final CharSequence USER_AGENT = createOptimized(HttpHeaderNames.USER_AGENT.toString());

  /**
   * Set-Cookie header name
   */
  public static final CharSequence SET_COOKIE = createOptimized(HttpHeaderNames.SET_COOKIE.toString());

  /**
   * application/x-www-form-urlencoded header value
   */
  public static final CharSequence APPLICATION_X_WWW_FORM_URLENCODED = createOptimized(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString());

  /**
   * chunked header value
   */
  public static final CharSequence CHUNKED = createOptimized(HttpHeaderValues.CHUNKED.toString());
  /**
   * close header value
   */
  public static final CharSequence CLOSE = createOptimized(HttpHeaderValues.CLOSE.toString());

  /**
   * 100-continue header value
   */
  public static final CharSequence CONTINUE = createOptimized(HttpHeaderValues.CONTINUE.toString());

  /**
   * identity header value
   */
  public static final CharSequence IDENTITY = createOptimized(HttpHeaderValues.IDENTITY.toString());
  /**
   * keep-alive header value
   */
  public static final CharSequence KEEP_ALIVE = createOptimized(HttpHeaderValues.KEEP_ALIVE.toString());

  /**
   * Upgrade header value
   */
  public static final CharSequence UPGRADE = createOptimized(HttpHeaderValues.UPGRADE.toString());
  /**
   * WebSocket header value
   */
  public static final CharSequence WEBSOCKET = createOptimized(HttpHeaderValues.WEBSOCKET.toString());

  /**
   * deflate,gzip header value
   */
  public static final CharSequence DEFLATE_GZIP = createOptimized("deflate, gzip");

  /**
   * text/html header value
   */
  public static final CharSequence TEXT_HTML = createOptimized("text/html");

  /**
   * GET header value
   */
  public static final CharSequence GET = createOptimized("GET");

  /**
   * Create an optimized {@link CharSequence} which can be used as header name or value.
   * This should be used if you expect to use it multiple times liked for example adding the same header name or value
   * for multiple responses or requests.
   */
  public static CharSequence createOptimized(String value) {
    HttpUtils.validateHeader(value);
    return new AsciiString(value);
  }

  private HttpHeaders() {
  }
}
