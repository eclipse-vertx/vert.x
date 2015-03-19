/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.spi.metrics;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

/**
 * The http server metrics SPI that Vert.x will use to call when each http server event occurs.
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface HttpServerMetrics<R, S> extends TCPMetrics<S> {

  /**
   * Called when an http server request begins
   *
   * @param request the {@link io.vertx.core.http.HttpServerRequest}
   * @param response the {@link io.vertx.core.http.HttpServerResponse}
   * @return the request metric
   */
  R requestBegin(HttpServerRequest request, HttpServerResponse response);

  /**
   * Called when an http server response has ended.
   *
   * @param requestMetric the request metric
   * @param response the {@link io.vertx.core.http.HttpServerResponse}
   */
  void responseEnd(R requestMetric, HttpServerResponse response);
}
