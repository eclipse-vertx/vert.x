/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.http;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.internal.streams.WriteResult;
import io.vertx.core.internal.streams.WriteStreamInternal;
import io.vertx.core.spi.metrics.ClientMetrics;

/**
 * Extends to expose internal methods that are necessary for integration.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpClientRequestInternal extends HttpClientRequest, WriteStreamInternal<Buffer> {

  /**
   * @return the request metric obtained from {@link ClientMetrics} for this request
   */
  Object metric();

  @Override
  WriteResult write(Buffer data);

  @Override
  WriteResult end(Buffer data);

  @Override
  WriteResult end();

  @Override
  HttpClientRequestInternal setWriteQueueMaxSize(int maxSize);

  @Override
  HttpClientRequestInternal drainHandler(Handler<Void> handler);

  @Override
  HttpClientRequestInternal exceptionHandler(Handler<Throwable> handler);

}
