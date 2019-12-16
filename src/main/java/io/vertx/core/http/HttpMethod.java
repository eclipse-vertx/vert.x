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

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an HTTP method
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
@VertxGen
public enum HttpMethod {
  // RFC 2616
  OPTIONS,
  GET,
  HEAD,
  POST,
  PUT,
  DELETE,
  TRACE,
  CONNECT,
  // RFC 5789
  PATCH,
  // RFC 2518
  // RFC 4918
  PROPFIND,
  PROPPATCH,
  MKCOL,
  COPY,
  MOVE,
  LOCK,
  UNLOCK,
  // RFC 4791
  MKCALENDAR,
  // RFC 3253
  VERSION_CONTROL("VERSION-CONTROL"),
  REPORT,
  CHECKOUT,
  CHECKIN,
  UNCHECKOUT,
  MKWORKSPACE,
  UPDATE,
  LABEL,
  MERGE,
  BASELINE_CONTROL("BASELINE-CONTROL"),
  MKACTIVITY,
  // RFC 3648
  ORDERPATCH,
  // RFC 3744
  ACL,
  // RFC 5323
  SEARCH,
  // Future
  OTHER;

  // will hold the HTTP method as defined at the protocol level
  private final String value;

  // default constructor to avoid duplicate tags
  HttpMethod() {
    this(null);
  }

  // override the default name as names in java must be valid identifiers
  // yet HTTP protocol verbs allows usage of non identifier symbols.
  HttpMethod(String value) {
    if (value == null) {
      this.value = name();
    } else {
      this.value = value;
    }
  }

  @Override
  public String toString() {
    // print the correct identifier
    return value;
  }

  private static final Map<String, HttpMethod> METHOD_MAP = new HashMap<>();

  static {
    for (HttpMethod method : HttpMethod.values()) {
      METHOD_MAP.put(method.toString(), method);
    }
  }

  public static HttpMethod fromHTTPProtocol(String name) {
    HttpMethod found = METHOD_MAP.get(name);
    if (found == null) {
      found = OTHER;
    }
    return found;
  }

}
