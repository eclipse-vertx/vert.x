/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.http;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.util.AsciiString;

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
  public static final CharSequence ACCEPT = HttpHeaderNames.ACCEPT;

  /**
   * Accept-Charset header name
   */
  public static final CharSequence ACCEPT_CHARSET = HttpHeaderNames.ACCEPT_CHARSET;

  /**
   * Accept-Encoding header name
   */
  public static final CharSequence ACCEPT_ENCODING = HttpHeaderNames.ACCEPT_ENCODING;

  /**
   * Accept-Language header name
   */
  public static final CharSequence ACCEPT_LANGUAGE = HttpHeaderNames.ACCEPT_LANGUAGE;

  /**
   * Accept-Ranges header name
   */
  public static final CharSequence ACCEPT_RANGES = HttpHeaderNames.ACCEPT_RANGES;

  /**
   * Accept-Patch header name
   */
  public static final CharSequence ACCEPT_PATCH = HttpHeaderNames.ACCEPT_PATCH;

  /**
   * Access-Control-Allow-Credentials header name
   */
  public static final CharSequence ACCESS_CONTROL_ALLOW_CREDENTIALS = HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS;

  /**
   * Access-Control-Allow-Headers header name
   */
  public static final CharSequence ACCESS_CONTROL_ALLOW_HEADERS = HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS;

  /**
   * Access-Control-Allow-Methods header name
   */
  public static final CharSequence ACCESS_CONTROL_ALLOW_METHODS = HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS;

  /**
   * Access-Control-Allow-Origin header name
   */
  public static final CharSequence ACCESS_CONTROL_ALLOW_ORIGIN = HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN;

  /**
   * Access-Control-Expose-Headers header name
   */
  public static final CharSequence ACCESS_CONTROL_EXPOSE_HEADERS = HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS;

  /**
   * Access-Control-Max-Age header name
   */
  public static final CharSequence ACCESS_CONTROL_MAX_AGE = HttpHeaderNames.ACCESS_CONTROL_MAX_AGE;

  /**
   * Access-Control-Request-Headers header name
   */
  public static final CharSequence ACCESS_CONTROL_REQUEST_HEADERS = HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS;

  /**
   * Access-Control-Request-Method header name
   */
  public static final CharSequence ACCESS_CONTROL_REQUEST_METHOD = HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD;

  /**
   *  Age header name
   */
  public static final CharSequence AGE = HttpHeaderNames.AGE;

  /**
   * Allow header name
   */
  public static final CharSequence ALLOW = HttpHeaderNames.ALLOW;

  /**
   * Authorization header name
   */
  public static final CharSequence AUTHORIZATION = HttpHeaderNames.AUTHORIZATION;

  /**
   * Cache-Control header name
   */
  public static final CharSequence CACHE_CONTROL = HttpHeaderNames.CACHE_CONTROL;

  /**
   * Connection header name
   */
  public static final CharSequence CONNECTION = HttpHeaderNames.CONNECTION;

  /**
   * Content-Base header name
   */
  public static final CharSequence CONTENT_BASE = HttpHeaderNames.CONTENT_BASE;

  /**
   * Content-Encoding header name
   */
  public static final CharSequence CONTENT_ENCODING = HttpHeaderNames.CONTENT_ENCODING;

  /**
   * Content-Language header name
   */
  public static final CharSequence CONTENT_LANGUAGE = HttpHeaderNames.CONTENT_LANGUAGE;

  /**
   * Content-Length header name
   */
  public static final CharSequence CONTENT_LENGTH = HttpHeaderNames.CONTENT_LENGTH;

  /**
   * Content-Location header name
   */
  public static final CharSequence CONTENT_LOCATION = HttpHeaderNames.CONTENT_LOCATION;

  /**
   * Content-MD5 header name
   */
  public static final CharSequence CONTENT_MD5 = HttpHeaderNames.CONTENT_MD5;

  /**
   * Content-Rage header name
   */
  public static final CharSequence CONTENT_RANGE = HttpHeaderNames.CONTENT_RANGE;

  /**
   * Content-Transfer-Encoding header name
   */
  public static final CharSequence CONTENT_TRANSFER_ENCODING = HttpHeaderNames.CONTENT_TRANSFER_ENCODING;

  /**
   * Content-Type header name
   */
  public static final CharSequence CONTENT_TYPE = HttpHeaderNames.CONTENT_TYPE;

  /**
   * Content-Cookie header name
   */
  public static final CharSequence COOKIE = HttpHeaderNames.COOKIE;

  /**
   * Date header name
   */
  public static final CharSequence DATE = HttpHeaderNames.DATE;

  /**
   * Etag header name
   */
  public static final CharSequence ETAG = HttpHeaderNames.ETAG;

  /**
   * Expect header name
   */
  public static final CharSequence EXPECT = HttpHeaderNames.EXPECT;

  /**
   * Expires header name
   */
  public static final CharSequence EXPIRES = HttpHeaderNames.EXPIRES;

  /**
   * From header name
   */
  public static final CharSequence FROM = HttpHeaderNames.FROM;

  /**
   * Host header name
   */
  public static final CharSequence HOST = HttpHeaderNames.HOST;

  /**
   * If-Match header name
   */
  public static final CharSequence IF_MATCH = HttpHeaderNames.IF_MATCH;

  /**
   * If-Modified-Since header name
   */
  public static final CharSequence IF_MODIFIED_SINCE = HttpHeaderNames.IF_MODIFIED_SINCE;

  /**
   * If-None-Match header name
   */
  public static final CharSequence IF_NONE_MATCH = HttpHeaderNames.IF_NONE_MATCH;

  /**
   * If-Unmodified-Since header name
   */
  public static final CharSequence IF_UNMODIFIED_SINCE = HttpHeaderNames.IF_UNMODIFIED_SINCE;

  /**
   * Last-Modified header name
   */
  public static final CharSequence LAST_MODIFIED = HttpHeaderNames.LAST_MODIFIED;

  /**
   * Location header name
   */
  public static final CharSequence LOCATION = HttpHeaderNames.LOCATION;

  /**
   * Origin header name
   */
  public static final CharSequence ORIGIN = HttpHeaderNames.ORIGIN;

  /**
   * Pragma header name
   */
  public static final CharSequence PRAGMA = HttpHeaderNames.PRAGMA;

  /**
   * Proxy-Authenticate header name
   */
  public static final CharSequence PROXY_AUTHENTICATE = HttpHeaderNames.PROXY_AUTHENTICATE;

  /**
   * Proxy-Authorization header name
   */
  public static final CharSequence PROXY_AUTHORIZATION = HttpHeaderNames.PROXY_AUTHORIZATION;

  /**
   * Range header name
   */
  public static final CharSequence RANGE = HttpHeaderNames.RANGE;

  /**
   * Referer header name
   */
  public static final CharSequence REFERER = HttpHeaderNames.REFERER;

  /**
   * Retry-After header name
   */
  public static final CharSequence RETRY_AFTER = HttpHeaderNames.RETRY_AFTER;

  /**
   * Server header name
   */
  public static final CharSequence SERVER = HttpHeaderNames.SERVER;

  /**
   * TE header name
   */
  public static final CharSequence TE = HttpHeaderNames.TE;

  /**
   * Trailer header name
   */
  public static final CharSequence TRAILER = HttpHeaderNames.TRAILER;

  /**
   * Transfer-Encoding header name
   */
  public static final CharSequence TRANSFER_ENCODING = HttpHeaderNames.TRANSFER_ENCODING;

  /**
   * User-Agent header name
   */
  public static final CharSequence USER_AGENT = HttpHeaderNames.USER_AGENT;

  /**
   * Vary header name
   */
  public static final CharSequence VARY = HttpHeaderNames.VARY;

  /**
   * Via header name
   */
  public static final CharSequence VIA = HttpHeaderNames.VIA;

  /**
   * Warning header name
   */
  public static final CharSequence WARNING = HttpHeaderNames.WARNING;

  /**
   * Set-Cookie header name
   */
  public static final CharSequence SET_COOKIE = HttpHeaderNames.SET_COOKIE;

  /**
   * application/x-www-form-urlencoded header value
   */
  public static final CharSequence APPLICATION_X_WWW_FORM_URLENCODED = HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED;

  /**
   * chunked header value
   */
  public static final CharSequence CHUNKED =  HttpHeaderValues.CHUNKED;
  /**
   * close header value
   */
  public static final CharSequence CLOSE =  HttpHeaderValues.CLOSE;

  /**
   * 100-continue header value
   */
  public static final CharSequence CONTINUE =  HttpHeaderValues.CONTINUE;

  /**
   * identity header value
   */
  public static final CharSequence IDENTITY =  HttpHeaderValues.IDENTITY;
  /**
   * keep-alive header value
   */
  public static final CharSequence KEEP_ALIVE = HttpHeaderValues.KEEP_ALIVE;

  /**
   * Upgrade header value
   */
  public static final CharSequence UPGRADE = HttpHeaderValues.UPGRADE;
  /**
   * WebSocket header value
   */
  public static final CharSequence WEBSOCKET = HttpHeaderValues.WEBSOCKET;

  /**
   * deflate,gzip header value
   *
   * Deprecated: use {@link #GZIP_DEFLATE} instead.
   */
  @Deprecated
  public static final CharSequence DEFLATE_GZIP = createOptimized("deflate, gzip");

  /**
   * gzip,deflate header value
   */
  public static final CharSequence GZIP_DEFLATE = HttpHeaderValues.GZIP_DEFLATE;

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
    return new AsciiString(value);
  }

  private HttpHeaders() {
  }
}
