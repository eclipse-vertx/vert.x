/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
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
