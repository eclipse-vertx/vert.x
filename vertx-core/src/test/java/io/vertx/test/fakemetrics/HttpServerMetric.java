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

package io.vertx.test.fakemetrics;

import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpServerMetric {

  public final String uri;
  public final SocketMetric socket;
  public final AtomicBoolean failed = new AtomicBoolean();
  public final AtomicReference<String> route = new AtomicReference<>();
  public final HttpRequest request;
  public final AtomicBoolean requestEnded = new AtomicBoolean();
  public final AtomicLong bytesRead = new AtomicLong();
  public final AtomicReference<HttpResponse> response = new AtomicReference<>();
  public final AtomicBoolean responseEnded = new AtomicBoolean();
  public final AtomicLong bytesWritten = new AtomicLong();

  public HttpServerMetric(String uri, SocketMetric socket) {
    this.uri = uri;
    this.request = null;
    this.socket = socket;
  }

  public HttpServerMetric(HttpRequest request, SocketMetric socket) {
    this.uri = request.uri();
    this.request = request;
    this.socket = socket;
  }
}
