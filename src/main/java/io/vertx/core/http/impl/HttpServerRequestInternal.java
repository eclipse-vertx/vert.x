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

import io.vertx.core.Context;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.spi.observability.HttpRequest;

/**
 * Extends to expose internal methods that are necessary for integration.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpServerRequestInternal extends HttpServerRequest {

  /**
   * @return the Vert.x context associated with this server request
   */
  Context context();

  /**
   * @return the metric object returned by the {@link io.vertx.core.spi.metrics.HttpServerMetrics#requestBegin(Object, HttpRequest)} call
   */
  Object metric();

}
