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
package io.vertx.core.http.impl;

import io.vertx.core.http.HttpMethod;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class HttpMethodImpl implements HttpMethod {

  public static io.netty.handler.codec.http.HttpMethod toNetty(HttpMethod method) {
    if (method instanceof HttpMethodImpl) {
      return ((HttpMethodImpl) method).nettyMethod;
    } else {
      return io.netty.handler.codec.http.HttpMethod.valueOf(method.name());
    }
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
        return new HttpMethodImpl(sMethod);
    }
  }

  private final io.netty.handler.codec.http.HttpMethod nettyMethod;

  public HttpMethodImpl(io.netty.handler.codec.http.HttpMethod nettyMethod) {
    this.nettyMethod = nettyMethod;
  }

  @Override
  public String name() {
    return nettyMethod.name();
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

  @Override
  public String toString() {
    return name();
  }
}
