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

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.spi.observability.HttpResponse;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpResponseHead implements HttpResponse {

  public final HttpVersion version;
  public final int statusCode;
  public final String statusMessage;
  public final MultiMap headers;

  public HttpResponseHead(HttpVersion version, int statusCode, String statusMessage, MultiMap headers) {
    this.version = version;
    this.statusCode = statusCode;
    this.statusMessage = statusMessage;
    this.headers = headers;
  }

  @Override
  public int statusCode() {
    return statusCode;
  }

  @Override
  public MultiMap headers() {
    return headers;
  }
}
