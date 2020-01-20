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

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.http.impl.HttpMethodImpl;

import java.util.List;
import java.util.Objects;

/**
 * Represents an HTTP method.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
@VertxGen
public interface HttpMethod {

  /**
   * The RFC 2616 {@code OPTIONS} method, this instance is interned and uniquely used.
   */
  HttpMethod OPTIONS = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.OPTIONS);

  /**
   * The RFC 2616 {@code GET} method, this instance is interned and uniquely used.
   */
  HttpMethod GET = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.GET);

  /**
   * The RFC 2616 {@code HEAD} method, this instance is interned and uniquely used.
   */
  HttpMethod HEAD = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.HEAD);

  /**
   * The {RFC 2616 @code POST} method, this instance is interned and uniquely used.
   */
  HttpMethod POST = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.POST);

  /**
   * The RFC 2616 {@code PUT} method, this instance is interned and uniquely used.
   */
  HttpMethod PUT = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.PUT);

  /**
   * The RFC 2616 {@code DELETE} method, this instance is interned and uniquely used.
   */
  HttpMethod DELETE = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.DELETE);

  /**
   * The RFC 2616 {@code TRACE} method, this instance is interned and uniquely used.
   */
  HttpMethod TRACE = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.TRACE);

  /**
   * The RFC 2616 {@code CONNECT} method, this instance is interned and uniquely used.
   */
  HttpMethod CONNECT = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.CONNECT);

  /**
   * The RFC 5789 {@code PATCH} method, this instance is interned and uniquely used.
   */
  HttpMethod PATCH = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.PATCH);

  /**
   * The RFC 2518/4918 {@code PROPFIND} method, this instance is interned and uniquely used.
   */
  HttpMethod PROPFIND = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("PROPFIND"));

  /**
   * The RFC 2518/4918 {@code PROPPATCH} method, this instance is interned and uniquely used.
   */
  HttpMethod PROPPATCH = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("PROPPATCH"));

  /**
   * The RFC 2518/4918 {@code MKCOL} method, this instance is interned and uniquely used.
   */
  HttpMethod MKCOL = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("MKCOL"));

  /**
   * The RFC 2518/4918 {@code COPY} method, this instance is interned and uniquely used.
   */
  HttpMethod COPY = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("COPY"));

  /**
   * The RFC 2518/4918 {@code MOVE} method, this instance is interned and uniquely used.
   */
  HttpMethod MOVE = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("MOVE"));

  /**
   * The RFC 2518/4918 {@code LOCK} method, this instance is interned and uniquely used.
   */
  HttpMethod LOCK = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("LOCK"));

  /**
   * The RFC 2518/4918 {@code UNLOCK} method, this instance is interned and uniquely used.
   */
  HttpMethod UNLOCK = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("UNLOCK"));

  /**
   * The RFC 4791 {@code MKCALENDAR} method, this instance is interned and uniquely used.
   */
  HttpMethod MKCALENDAR = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("MKCALENDAR"));

  /**
   * The RFC 3253 {@code VERSION_CONTROL} method, this instance is interned and uniquely used.
   */
  HttpMethod VERSION_CONTROL = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("VERSION-CONTROL"));

  /**
   * The RFC 3253 {@code REPORT} method, this instance is interned and uniquely used.
   */
  HttpMethod REPORT = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("REPORT"));

  /**
   * The RFC 3253 {@code CHECKOUT} method, this instance is interned and uniquely used.
   */
  HttpMethod CHECKOUT = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("CHECKOUT"));

  /**
   * The RFC 3253 {@code CHECKIN} method, this instance is interned and uniquely used.
   */
  HttpMethod CHECKIN = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("CHECKIN"));

  /**
   * The RFC 3253 {@code UNCHECKOUT} method, this instance is interned and uniquely used.
   */
  HttpMethod UNCHECKOUT = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("UNCHECKOUT"));

  /**
   * The RFC 3253 {@code MKWORKSPACE} method, this instance is interned and uniquely used.
   */
  HttpMethod MKWORKSPACE = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("MKWORKSPACE"));

  /**
   * The RFC 3253 {@code UPDATE} method, this instance is interned and uniquely used.
   */
  HttpMethod UPDATE = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("UPDATE"));

  /**
   * The RFC 3253 {@code LABEL} method, this instance is interned and uniquely used.
   */
  HttpMethod LABEL = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("LABEL"));

  /**
   * The RFC 3253 {@code MERGE} method, this instance is interned and uniquely used.
   */
  HttpMethod MERGE = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("MERGE"));

  /**
   * The RFC 3253 {@code BASELINE_CONTROL} method, this instance is interned and uniquely used.
   */
  HttpMethod BASELINE_CONTROL = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("BASELINE-CONTROL"));

  /**
   * The RFC 3253 {@code MKACTIVITY} method, this instance is interned and uniquely used.
   */
  HttpMethod MKACTIVITY = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("MKACTIVITY"));

  /**
   * The RFC 3648 {@code ORDERPATCH} method, this instance is interned and uniquely used.
   */
  HttpMethod ORDERPATCH = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("ORDERPATCH"));

  /**
   * The RFC 3744 {@code ACL} method, this instance is interned and uniquely used.
   */
  HttpMethod ACL = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("ACL"));

  /**
   * The RFC 5323 {@code SEARCH} method, this instance is interned and uniquely used.
   */
  HttpMethod SEARCH = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf("SEARCH"));



  /**
   * @return the method name
   */
  String name();

  /**
   * @return the same value than {@link #name()}
   */
  String toString();

  /**
   * Lookup the {@code HttpMethod} value for the specified {@code value}.
   * <br/>
   * The predefined method constants {@link #GET}, {@link #POST}, {@link #PUT}, {@link #HEAD}, {@link #OPTIONS},
   * {@link #DELETE}, {@link #TRACE}, {@link #CONNECT} and {@link #PATCH} are interned and will be returned
   * when case sensitively matching their string value (i.e {@code "GET"}, etc...)
   * <br/>
   * Otherwise a new instance is returned.
   *
   * @param value the value
   * @return the {@code HttpMethod} instance for the specified string {@code value}
   * @throws IllegalArgumentException when the value is incorrect, the value is empty or contains an invalid char
   */
  static HttpMethod valueOf(String value) {
    Objects.requireNonNull(value, "value");
    switch (value) {
      case "OPTIONS":
        return OPTIONS;
      case "GET":
        return GET;
      case "HEAD":
        return HEAD;
      case "POST":
        return POST;
      case "PUT":
        return PUT;
      case "DELETE":
        return DELETE;
      case "TRACE":
        return TRACE;
      case "CONNECT":
        return CONNECT;
      case "PATCH":
        return PATCH;
      case "PROPFIND":
        return PROPFIND;
      case "PROPPATCH":
        return PROPPATCH;
      case "MKCOL":
        return MKCOL;
      case "COPY":
        return COPY;
      case "MOVE":
        return MOVE;
      case "LOCK":
        return LOCK;
      case "UNLOCK":
        return UNLOCK;
      case "MKCALENDAR":
        return MKCALENDAR;
      case "VERSION-CONTROL":
        return VERSION_CONTROL;
      case "REPORT":
        return REPORT;
      case "CHECKOUT":
        return CHECKOUT;
      case "CHECKIN":
        return CHECKIN;
      case "UNCHECKOUT":
        return UNCHECKOUT;
      case "MKWORKSPACE":
        return MKWORKSPACE;
      case "UPDATE":
        return UPDATE;
      case "LABEL":
        return LABEL;
      case "MERGE":
        return MERGE;
      case "BASELINE-CONTROL":
        return BASELINE_CONTROL;
      case "MKACTIVITY":
        return MKACTIVITY;
      case "ORDERPATCH":
        return ORDERPATCH;
      case "ACL":
        return ACL;
      case "SEARCH":
        return SEARCH;
      default:
        return new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf(value));
    }
  }

  /**
   * @return an un-modifiable list of known HTTP methods
   */
  static List<HttpMethod> values() {
    return HttpMethods.ALL;
  }
}
