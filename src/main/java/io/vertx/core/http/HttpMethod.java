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

import java.util.Objects;

/**
 * Represents an HTTP method.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface HttpMethod {

  /**
   * The {@code OPTIONS} method, this instance is interned and uniquely used.
   */
  HttpMethod OPTIONS = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.OPTIONS);

  /**
   * The {@code GET} method, this instance is interned and uniquely used.
   */
  HttpMethod GET = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.GET);

  /**
   * The {@code HEAD} method, this instance is interned and uniquely used.
   */
  HttpMethod HEAD = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.HEAD);

  /**
   * The {@code POST} method, this instance is interned and uniquely used.
   */
  HttpMethod POST = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.POST);

  /**
   * The {@code PUT} method, this instance is interned and uniquely used.
   */
  HttpMethod PUT = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.PUT);

  /**
   * The {@code DELETE} method, this instance is interned and uniquely used.
   */
  HttpMethod DELETE = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.DELETE);

  /**
   * The {@code TRACE} method, this instance is interned and uniquely used.
   */
  HttpMethod TRACE = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.TRACE);

  /**
   * The {@code CONNECT} method, this instance is interned and uniquely used.
   */
  HttpMethod CONNECT = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.CONNECT);

  /**
   * The {@code PATCH} method, this instance is interned and uniquely used.
   */
  HttpMethod PATCH = new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.PATCH);

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
      default:
        return new HttpMethodImpl(io.netty.handler.codec.http.HttpMethod.valueOf(value));
    }
  }
}
