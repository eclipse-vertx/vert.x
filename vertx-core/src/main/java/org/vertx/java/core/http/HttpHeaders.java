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
package org.vertx.java.core.http;

/**
 * Contains often used Header names. Beside this contains a utility method to create optimized
 * {@link CharSequence} which can be used as header name and value.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class HttpHeaders {


  /**
   * Accept header name
   */
  public static final CharSequence ACCEPT = io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT;

  /**
   * Accept-Charset header name
   */
  public static final CharSequence ACCEPT_CHARSET = io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT_CHARSET;

  /**
   * Accept-Encoding header name
   */
  public static final CharSequence ACCEPT_ENCODING = io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT_ENCODING;

  /**
   * Accept-Language header name
   */
  public static final CharSequence ACCEPT_LANGUAGE = io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT_LANGUAGE;

  /**
   * Accept-Ranges header name
   */
  public static final CharSequence ACCEPT_RANGES = io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT_RANGES;

  /**
   * Accept-Patch header name
   */
  public static final CharSequence ACCEPT_PATCH = io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT_PATCH;

  /**
   * Access-Control-Allow-Credentials header name
   */
  public static final CharSequence ACCESS_CONTROL_ALLOW_CREDENTIALS = io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_CREDENTIALS;

  /**
   * Access-Control-Allow-Headers header name
   */
  public static final CharSequence ACCESS_CONTROL_ALLOW_HEADERS = io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_HEADERS;

  /**
   * Access-Control-Allow-Methods header name
   */
  public static final CharSequence ACCESS_CONTROL_ALLOW_METHODS = io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_METHODS;

  /**
   * Access-Control-Allow-Origin header name
   */
  public static final CharSequence ACCESS_CONTROL_ALLOW_ORIGIN = io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN;

  /**
   * Access-Control-Expose-Headers header name
   */
  public static final CharSequence ACCESS_CONTROL_EXPOSE_HEADERS = io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_EXPOSE_HEADERS;

  /**
   * Access-Control-Max-Age header name
   */
  public static final CharSequence ACCESS_CONTROL_MAX_AGE = io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_MAX_AGE;

  /**
   * Access-Control-Request-Headers header name
   */
  public static final CharSequence ACCESS_CONTROL_REQUEST_HEADERS = io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_REQUEST_HEADERS;

  /**
   * Access-Control-Request-Method header name
   */
  public static final CharSequence ACCESS_CONTROL_REQUEST_METHOD = io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_REQUEST_METHOD;

  /**
   *  Age header name
   */
  public static final CharSequence AGE = io.netty.handler.codec.http.HttpHeaders.Names.AGE;

  /**
   * Allow header name
   */
  public static final CharSequence ALLOW = io.netty.handler.codec.http.HttpHeaders.Names.ALLOW;

  /**
   * Authorization header name
   */
  public static final CharSequence AUTHORIZATION = io.netty.handler.codec.http.HttpHeaders.Names.AUTHORIZATION;

  /**
   * Cache-Control header name
   */
  public static final CharSequence CACHE_CONTROL = io.netty.handler.codec.http.HttpHeaders.Names.CACHE_CONTROL;

  /**
   * Connection header name
   */
  public static final CharSequence CONNECTION = io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;

  /**
   * Content-Base header name
   */
  public static final CharSequence CONTENT_BASE = io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_BASE;

  /**
   * Content-Encoding header name
   */
  public static final CharSequence CONTENT_ENCODING = io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_ENCODING;

  /**
   * Content-Language header name
   */
  public static final CharSequence CONTENT_LANGUAGE = io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LANGUAGE;

  /**
   * Content-Length header name
   */
  public static final CharSequence CONTENT_LENGTH = io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;

  /**
   * Content-Location header name
   */
  public static final CharSequence CONTENT_LOCATION = io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LOCATION;

  /**
   * Content-Transfer-Encoding header name
   */
  public static final CharSequence CONTENT_TRANSFER_ENCODING = io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TRANSFER_ENCODING;

  /**
   * Content-MD5 header name
   */
  public static final CharSequence CONTENT_MD5 = io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_MD5;

  /**
   * Content-Rage header name
   */
  public static final CharSequence CONTENT_RANGE = io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_RANGE;

  /**
   * Content-Type header name
   */
  public static final CharSequence CONTENT_TYPE = io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;

  /**
   * Content-Cookie header name
   */
  public static final CharSequence COOKIE = io.netty.handler.codec.http.HttpHeaders.Names.COOKIE;

  /**
   * Date header name
   */
  public static final CharSequence DATE = io.netty.handler.codec.http.HttpHeaders.Names.DATE;

  /**
   * Etag header name
   */
  public static final CharSequence ETAG = io.netty.handler.codec.http.HttpHeaders.Names.ETAG;

  /**
   * Expect header name
   */
  public static final CharSequence EXPECT = io.netty.handler.codec.http.HttpHeaders.Names.EXPECT;

  /**
   * Expires header name
   */
  public static final CharSequence EXPIRES = io.netty.handler.codec.http.HttpHeaders.Names.EXPIRES;

  /**
   * From header name
   */
  public static final CharSequence FROM = io.netty.handler.codec.http.HttpHeaders.Names.FROM;

  /**
   * Host header name
   */
  public static final CharSequence HOST = io.netty.handler.codec.http.HttpHeaders.Names.HOST;

  /**
   * If-Match header name
   */
  public static final CharSequence IF_MATCH = io.netty.handler.codec.http.HttpHeaders.Names.IF_MATCH;

  /**
   * If-Modified-Since header name
   */
  public static final CharSequence IF_MODIFIED_SINCE = io.netty.handler.codec.http.HttpHeaders.Names.IF_MODIFIED_SINCE;

  /**
   * If-None-Match header name
   */
  public static final CharSequence IF_NONE_MATCH = io.netty.handler.codec.http.HttpHeaders.Names.IF_NONE_MATCH;

  /**
   * Last-Modified header name
   */
  public static final CharSequence LAST_MODIFIED = io.netty.handler.codec.http.HttpHeaders.Names.LAST_MODIFIED;

  /**
   * Location header name
   */
  public static final CharSequence LOCATION = io.netty.handler.codec.http.HttpHeaders.Names.LOCATION;

  /**
   * Origin header name
   */
  public static final CharSequence ORIGIN = io.netty.handler.codec.http.HttpHeaders.Names.ORIGIN;

  /**
   * Proxy-Authenticate header name
   */
  public static final CharSequence PROXY_AUTHENTICATE = io.netty.handler.codec.http.HttpHeaders.Names.PROXY_AUTHENTICATE;

  /**
   * Proxy-Authorization header name
   */
  public static final CharSequence PROXY_AUTHORIZATION = io.netty.handler.codec.http.HttpHeaders.Names.PROXY_AUTHORIZATION;

  /**
   * Referer header name
   */
  public static final CharSequence REFERER = io.netty.handler.codec.http.HttpHeaders.Names.REFERER;

  /**
   * Retry-After header name
   */
  public static final CharSequence RETRY_AFTER = io.netty.handler.codec.http.HttpHeaders.Names.RETRY_AFTER;

  /**
   * Server header name
   */
  public static final CharSequence SERVER = io.netty.handler.codec.http.HttpHeaders.Names.SERVER;

  /**
   * Transfer-Encoding header name
   */
  public static final CharSequence TRANSFER_ENCODING = io.netty.handler.codec.http.HttpHeaders.Names.TRANSFER_ENCODING;

  /**
   * Upgrade header name
   */
  public static final CharSequence UPGRADE = io.netty.handler.codec.http.HttpHeaders.Names.UPGRADE;

  /**
   * User-Agent header name
   */
  public static final CharSequence USER_AGENT = io.netty.handler.codec.http.HttpHeaders.Names.USER_AGENT;


  /**
   * Create an optimized {@link java.lang.CharSequence} which can be used as header name or value.
   * This should be used if you expect to use it multiple times liked for example adding the same header name or value
   * for multiple responses or requests.
   */
  public static CharSequence createOptimized(String value) {
    return io.netty.handler.codec.http.HttpHeaders.newEntity(value);
  }

  private HttpHeaders() {
  }
}
