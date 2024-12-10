/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.internal.http;

import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.http.*;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.spi.metrics.MetricsProvider;

import java.util.function.Function;

/**
 * Http client internal API.
 */
public interface HttpClientInternal extends HttpClientAgent, MetricsProvider, Closeable {

  /**
   * @return the vertx, for use in package related classes only.
   */
  VertxInternal vertx();

  Function<HttpClientResponse, Future<RequestOptions>> redirectHandler();

  HttpClientOptions options();

  NetClientInternal netClient();

  Future<Void> closeFuture();

}
