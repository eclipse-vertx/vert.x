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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpClientMetric {

  public final EndpointMetric endpoint;
  public final HttpRequest request;
  public final AtomicInteger requestEnded = new AtomicInteger();
  public final AtomicInteger responseBegin = new AtomicInteger();
  public final AtomicLong bytesRead = new AtomicLong();
  public final AtomicLong bytesWritten = new AtomicLong();
  public final AtomicBoolean failed = new AtomicBoolean();

  public HttpClientMetric(EndpointMetric endpoint, HttpRequest request) {
    this.endpoint = endpoint;
    this.request = request;
  }
}
