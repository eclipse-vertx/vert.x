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

package io.vertx.core.metrics.spi;

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

/**
 * The http client metrics SPI that Vert.x will use to call when http client events occur.
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface HttpClientMetrics extends NetMetrics {

  /**
   * Called when an http client request begins
   * 
   * @param request the {@link io.vertx.core.http.HttpClientRequest}
   */
  void requestBegin(HttpClientRequest request);

  /**
   * Called when an http client response has ended
   *
   * @param request the {@link io.vertx.core.http.HttpClientRequest}
   * @param response the {@link io.vertx.core.http.HttpClientResponse}
   */
  void responseEnd(HttpClientRequest request, HttpClientResponse response);
}
