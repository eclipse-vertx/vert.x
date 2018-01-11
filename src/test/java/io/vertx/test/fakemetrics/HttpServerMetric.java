/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.fakemetrics;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpServerMetric {

  public final HttpServerRequest request;
  public final SocketMetric socket;
  public final AtomicBoolean failed = new AtomicBoolean();
  public final AtomicReference<HttpServerResponse> response = new AtomicReference<>();

  public HttpServerMetric(HttpServerRequest request, SocketMetric socket) {
    this.request = request;
    this.socket = socket;
  }
}
