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
  public static final CharSequence ACCEPT = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT);

  /**
   * Accept-Charset header name
   */
  public static final CharSequence ACCEPT_CHARSET = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT_CHARSET);

  /**
   * Accept-Encoding header name
   */
  public static final CharSequence ACCEPT_ENCODING = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT_ENCODING);

  /**
   * Accept-Language header name
   */
  public static final CharSequence ACCEPT_LANGUAGE = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT_LANGUAGE);

  /**
   * Accept-Ranges header name
   */
  public static final CharSequence ACCEPT_RANGES = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT_RANGES);

  /**
   * Accept-Patch header name
   */
  public static final CharSequence ACCEPT_PATCH = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT_PATCH);

  /**
   * Access-Control-Allow-Credentials header name
   */
  public static final CharSequence ACCESS_CONTROL_ALLOW_CREDENTIALS = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_CREDENTIALS);

  /**
   * Access-Control-Allow-Headers header name
   */
  public static final CharSequence ACCESS_CONTROL_ALLOW_HEADERS = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_HEADERS);

  /**
   * Access-Control-Allow-Methods header name
   */
  public static final CharSequence ACCESS_CONTROL_ALLOW_METHODS = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_METHODS);

  /**
   * Access-Control-Allow-Origin header name
   */
  public static final CharSequence ACCESS_CONTROL_ALLOW_ORIGIN = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN);

  /**
   * Access-Control-Expose-Headers header name
   */
  public static final CharSequence ACCESS_CONTROL_EXPOSE_HEADERS = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_EXPOSE_HEADERS);

  /**
   * Access-Control-Max-Age header name
   */
  public static final CharSequence ACCESS_CONTROL_MAX_AGE = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_MAX_AGE);

  /**
   * Access-Control-Request-Headers header name
   */
  public static final CharSequence ACCESS_CONTROL_REQUEST_HEADERS = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_REQUEST_HEADERS);

  /**
   * Access-Control-Request-Method header name
   */
  public static final CharSequence ACCESS_CONTROL_REQUEST_METHOD = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_REQUEST_METHOD);

  /**
   *  Age header name
   */
  public static final CharSequence AGE = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.AGE);

  /**
   * Allow header name
   */
  public static final CharSequence ALLOW = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.ALLOW);

  /**
   * Authorization header name
   */
  public static final CharSequence AUTHORIZATION = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.AUTHORIZATION);

  /**
   * Cache-Control header name
   */
  public static final CharSequence CACHE_CONTROL = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.CACHE_CONTROL);

  /**
   * Connection header name
   */
  public static final CharSequence CONNECTION = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION);

  /**
   * Content-Base header name
   */
  public static final CharSequence CONTENT_BASE = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_BASE);

  /**
   * Content-Encoding header name
   */
  public static final CharSequence CONTENT_ENCODING = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_ENCODING);

  /**
   * Content-Language header name
   */
  public static final CharSequence CONTENT_LANGUAGE = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LANGUAGE);

  /**
   * Content-Length header name
   */
  public static final CharSequence CONTENT_LENGTH = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH);

  /**
   * Content-Location header name
   */
  public static final CharSequence CONTENT_LOCATION = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LOCATION);

  /**
   * Content-Transfer-Encoding header name
   */
  public static final CharSequence CONTENT_TRANSFER_ENCODING = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TRANSFER_ENCODING);

  /**
   * Content-MD5 header name
   */
  public static final CharSequence CONTENT_MD5 = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_MD5);

  /**
   * Content-Rage header name
   */
  public static final CharSequence CONTENT_RANGE = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_RANGE);

  /**
   * Content-Type header name
   */
  public static final CharSequence CONTENT_TYPE = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE);

  /**
   * Content-Cookie header name
   */
  public static final CharSequence COOKIE = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.COOKIE);

  /**
   * Date header name
   */
  public static final CharSequence DATE = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.DATE);

  /**
   * Etag header name
   */
  public static final CharSequence ETAG = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.ETAG);

  /**
   * Expect header name
   */
  public static final CharSequence EXPECT = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.EXPECT);

  /**
   * Expires header name
   */
  public static final CharSequence EXPIRES = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.EXPIRES);

  /**
   * From header name
   */
  public static final CharSequence FROM = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.FROM);

  /**
   * Host header name
   */
  public static final CharSequence HOST = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.HOST);

  /**
   * If-Match header name
   */
  public static final CharSequence IF_MATCH = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.IF_MATCH);

  /**
   * If-Modified-Since header name
   */
  public static final CharSequence IF_MODIFIED_SINCE = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.IF_MODIFIED_SINCE);

  /**
   * If-None-Match header name
   */
  public static final CharSequence IF_NONE_MATCH = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.IF_NONE_MATCH);

  /**
   * Last-Modified header name
   */
  public static final CharSequence LAST_MODIFIED = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.LAST_MODIFIED);

  /**
   * Location header name
   */
  public static final CharSequence LOCATION = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.LOCATION);

  /**
   * Origin header name
   */
  public static final CharSequence ORIGIN = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.ORIGIN);

  /**
   * Proxy-Authenticate header name
   */
  public static final CharSequence PROXY_AUTHENTICATE = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.PROXY_AUTHENTICATE);

  /**
   * Proxy-Authorization header name
   */
  public static final CharSequence PROXY_AUTHORIZATION = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.PROXY_AUTHORIZATION);

  /**
   * Referer header name
   */
  public static final CharSequence REFERER = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.REFERER);

  /**
   * Retry-After header name
   */
  public static final CharSequence RETRY_AFTER = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.RETRY_AFTER);

  /**
   * Server header name
   */
  public static final CharSequence SERVER = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.SERVER);

  /**
   * Transfer-Encoding header name
   */
  public static final CharSequence TRANSFER_ENCODING = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.TRANSFER_ENCODING);

  /**
   * User-Agent header name
   */
  public static final CharSequence USER_AGENT = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.USER_AGENT);

  /**
   * Set-Cookie header name
   */
  public static final CharSequence SET_COOKIE = createOptimized(io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE);

  /**
   * application/x-www-form-urlencoded header value
   */
  public static final CharSequence APPLICATION_X_WWW_FORM_URLENCODED = createOptimized(io.netty.handler.codec.http.HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);

  /**
   * chunked header value
   */
  public static final CharSequence CHUNKED =  createOptimized(io.netty.handler.codec.http.HttpHeaders.Values.CHUNKED);
  /**
   * close header value
   */
  public static final CharSequence CLOSE =  createOptimized(io.netty.handler.codec.http.HttpHeaders.Values.CLOSE);

  /**
   * 100-continue header value
   */
  public static final CharSequence CONTINUE =  createOptimized(io.netty.handler.codec.http.HttpHeaders.Values.CONTINUE);

  /**
   * identity header value
   */
  public static final CharSequence IDENTITY =  createOptimized(io.netty.handler.codec.http.HttpHeaders.Values.IDENTITY);
  /**
   * keep-alive header value
   */
  public static final CharSequence KEEP_ALIVE = createOptimized(io.netty.handler.codec.http.HttpHeaders.Values.KEEP_ALIVE);

  /**
   * Upgrade header value
   */
  public static final CharSequence UPGRADE = createOptimized(io.netty.handler.codec.http.HttpHeaders.Values.UPGRADE);
  /**
   * WebSocket header value
   */
  public static final CharSequence WEBSOCKET = createOptimized(io.netty.handler.codec.http.HttpHeaders.Values.WEBSOCKET);

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
    return io.netty.handler.codec.http.HttpHeaders.newEntity(value);
  }

  private HttpHeaders() {
  }
}
