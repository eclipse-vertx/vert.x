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

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an HTTP method.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
@DataObject
public class HttpMethod {

  /**
   * The RFC 2616 {@code OPTIONS} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod OPTIONS;

  /**
   * The RFC 2616 {@code GET} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod GET;

  /**
   * The RFC 2616 {@code HEAD} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod HEAD;

  /**
   * The {RFC 2616 @code POST} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod POST;

  /**
   * The RFC 2616 {@code PUT} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod PUT;

  /**
   * The RFC 2616 {@code DELETE} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod DELETE;

  /**
   * The RFC 2616 {@code TRACE} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod TRACE;

  /**
   * The RFC 2616 {@code CONNECT} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod CONNECT;

  /**
   * The RFC 5789 {@code PATCH} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod PATCH;

  /**
   * The RFC 2518/4918 {@code PROPFIND} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod PROPFIND;

  /**
   * The RFC 2518/4918 {@code PROPPATCH} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod PROPPATCH;

  /**
   * The RFC 2518/4918 {@code MKCOL} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod MKCOL;

  /**
   * The RFC 2518/4918 {@code COPY} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod COPY;

  /**
   * The RFC 2518/4918 {@code MOVE} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod MOVE;

  /**
   * The RFC 2518/4918 {@code LOCK} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod LOCK;

  /**
   * The RFC 2518/4918 {@code UNLOCK} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod UNLOCK;

  /**
   * The RFC 4791 {@code MKCALENDAR} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod MKCALENDAR;

  /**
   * The RFC 3253 {@code VERSION_CONTROL} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod VERSION_CONTROL;

  /**
   * The RFC 3253 {@code REPORT} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod REPORT;

  /**
   * The RFC 3253 {@code CHECKOUT} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod CHECKOUT;

  /**
   * The RFC 3253 {@code CHECKIN} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod CHECKIN;

  /**
   * The RFC 3253 {@code UNCHECKOUT} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod UNCHECKOUT;

  /**
   * The RFC 3253 {@code MKWORKSPACE} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod MKWORKSPACE;

  /**
   * The RFC 3253 {@code UPDATE} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod UPDATE;

  /**
   * The RFC 3253 {@code LABEL} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod LABEL;

  /**
   * The RFC 3253 {@code MERGE} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod MERGE;

  /**
   * The RFC 3253 {@code BASELINE_CONTROL} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod BASELINE_CONTROL;

  /**
   * The RFC 3253 {@code MKACTIVITY} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod MKACTIVITY;

  /**
   * The RFC 3648 {@code ORDERPATCH} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod ORDERPATCH;

  /**
   * The RFC 3744 {@code ACL} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod ACL;

  /**
   * The RFC 5323 {@code SEARCH} method, this instance is interned and uniquely used.
   */
  public static final HttpMethod SEARCH;

  /**
   * All predefined methods.
   */
  private static final List<HttpMethod> ALL;

  static {
    OPTIONS = new HttpMethod(io.netty.handler.codec.http.HttpMethod.OPTIONS);
    GET = new HttpMethod(io.netty.handler.codec.http.HttpMethod.GET);
    HEAD = new HttpMethod(io.netty.handler.codec.http.HttpMethod.HEAD);
    POST = new HttpMethod(io.netty.handler.codec.http.HttpMethod.POST);
    PUT = new HttpMethod(io.netty.handler.codec.http.HttpMethod.PUT);
    DELETE = new HttpMethod(io.netty.handler.codec.http.HttpMethod.DELETE);
    TRACE = new HttpMethod(io.netty.handler.codec.http.HttpMethod.TRACE);
    CONNECT = new HttpMethod(io.netty.handler.codec.http.HttpMethod.CONNECT);
    PATCH = new HttpMethod(io.netty.handler.codec.http.HttpMethod.PATCH);
    PROPFIND = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("PROPFIND"));
    PROPPATCH = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("PROPPATCH"));
    MKCOL = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("MKCOL"));
    COPY = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("COPY"));
    MOVE = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("MOVE"));
    LOCK = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("LOCK"));
    UNLOCK = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("UNLOCK"));
    MKCALENDAR = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("MKCALENDAR"));
    VERSION_CONTROL = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("VERSION-CONTROL"));
    REPORT = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("REPORT"));
    CHECKOUT = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("CHECKOUT"));
    CHECKIN = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("CHECKIN"));
    UNCHECKOUT = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("UNCHECKOUT"));
    MKWORKSPACE = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("MKWORKSPACE"));
    UPDATE = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("UPDATE"));
    LABEL = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("LABEL"));
    MERGE = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("MERGE"));
    BASELINE_CONTROL = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("BASELINE-CONTROL"));
    MKACTIVITY = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("MKACTIVITY"));
    ORDERPATCH = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("ORDERPATCH"));
    ACL = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("ACL"));
    SEARCH = new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf("SEARCH"));
    // Create list after fields have been initialized
    ALL = Collections.unmodifiableList(Arrays.asList(
      HttpMethod.OPTIONS,
      HttpMethod.GET,
      HttpMethod.HEAD,
      HttpMethod.POST,
      HttpMethod.PUT,
      HttpMethod.DELETE,
      HttpMethod.TRACE,
      HttpMethod.CONNECT,
      HttpMethod.PATCH,
      HttpMethod.PROPFIND,
      HttpMethod.PROPPATCH,
      HttpMethod.MKCOL,
      HttpMethod.COPY,
      HttpMethod.MOVE,
      HttpMethod.LOCK,
      HttpMethod.UNLOCK,
      HttpMethod.MKCALENDAR,
      HttpMethod.VERSION_CONTROL,
      HttpMethod.REPORT,
      HttpMethod.CHECKIN,
      HttpMethod.CHECKOUT,
      HttpMethod.UNCHECKOUT,
      HttpMethod.MKWORKSPACE,
      HttpMethod.UPDATE,
      HttpMethod.LABEL,
      HttpMethod.MERGE,
      HttpMethod.BASELINE_CONTROL,
      HttpMethod.MKACTIVITY,
      HttpMethod.ORDERPATCH,
      HttpMethod.ACL,
      HttpMethod.SEARCH
    ));
  }

  /**
   * @return an un-modifiable list of known HTTP methods
   */
  public static List<HttpMethod> values() {
    return ALL;
  }

  /**
   * Lookup the {@code HttpMethod} value for the specified {@code nettyMethod}.
   * <br/>
   * The predefined method constants {@link #GET}, {@link #POST}, {@link #PUT}, {@link #HEAD}, {@link #OPTIONS},
   * {@link #DELETE}, {@link #TRACE}, {@link #CONNECT} and {@link #PATCH} are interned and will be returned
   * when case sensitively matching their string value (i.e {@code "GET"}, etc...)
   * <br/>
   * Otherwise a new instance is returned.
   *
   * @param method the netty method
   * @return the {@code HttpMethod} instance for the specified netty {@code method}
   */
  public static HttpMethod fromNetty(io.netty.handler.codec.http.HttpMethod method) {
    // Fast path
    if (method == io.netty.handler.codec.http.HttpMethod.GET) {
      return GET;
    } else if (method == io.netty.handler.codec.http.HttpMethod.POST) {
      return POST;
    } else {
      // Keep method small
      return _fromNetty(method);
    }
  }

  private static HttpMethod _fromNetty(io.netty.handler.codec.http.HttpMethod sMethod) {
    switch (sMethod.name()) {
      case "OPTIONS":
        return OPTIONS;
      case "HEAD":
        return HEAD;
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
        return new HttpMethod(sMethod);
    }
  }

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
  public static HttpMethod valueOf(String value) {
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
        return new HttpMethod(io.netty.handler.codec.http.HttpMethod.valueOf(value));
    }
  }

  private final io.netty.handler.codec.http.HttpMethod nettyMethod;

  public JsonObject toJson() {
    throw new UnsupportedOperationException();
  }

  public HttpMethod(String name) {
    Objects.requireNonNull(name, "HTTP method name");
    this.nettyMethod = io.netty.handler.codec.http.HttpMethod.valueOf(name);
  }

  private HttpMethod(io.netty.handler.codec.http.HttpMethod nettyMethod) {
    Objects.requireNonNull(nettyMethod, "HTTP method");
    this.nettyMethod = nettyMethod;
  }

  /**
   * @return the method name
   */
  public String name() {
    return nettyMethod.name();
  }

  /**
   * @return the same value than {@link #name()}
   */
  public String toString() {
    return nettyMethod.toString();
  }

  @Override
  public int hashCode() {
    return nettyMethod.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof HttpMethod) {
      HttpMethod that = (HttpMethod) obj;
      return Objects.equals(name(), that.name());
    }
    return false;
  }

  /**
   * @return the wrapped Netty method instance
   */
  public io.netty.handler.codec.http.HttpMethod toNetty() {
    return nettyMethod;
  }
}
