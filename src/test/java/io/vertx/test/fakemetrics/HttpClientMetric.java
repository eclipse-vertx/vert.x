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

import io.vertx.core.http.HttpClientRequest;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpClientMetric {

  public final EndpointMetric endpoint;
  public final HttpClientRequest request;
  public final SocketMetric socket;
  public final AtomicInteger requestEnded = new AtomicInteger();
  public final AtomicInteger responseBegin = new AtomicInteger();
  public final AtomicBoolean failed = new AtomicBoolean();

  public HttpClientMetric(EndpointMetric endpoint, HttpClientRequest request, SocketMetric socket) {
    this.endpoint = endpoint;
    this.request = request;
    this.socket = socket;
  }
}
