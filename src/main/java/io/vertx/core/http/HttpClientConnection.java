/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;

/**
 * Represents an HTTP client connection.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface HttpClientConnection extends HttpConnection {

  /**
   * Like {@link #createRequest(RequestOptions)} but with null options.
   */
  Future<HttpClientRequest> createRequest();

  /**
   * Create an HTTP request initialized with the specified request {@code options}
   *
   * This enqueues a request in the client connection queue, the resulting future is notified when the connection can satisfy
   * the request.
   *
   * Pooled HTTP connection will return an error, since requests should be made against the pool instead the connection itself.
   *
   * @return a future notified with the created request
   */
  Future<HttpClientRequest> createRequest(RequestOptions options);

}
