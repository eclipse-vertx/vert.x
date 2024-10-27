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

import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;

import java.util.concurrent.TimeUnit;

/**
 * Represents an HTTP client connection.
 * <p>
 * You can use this connection to create requests to the connected server.
 * <p>
 * Depending on the nature of the connection, requests might just be sent to the server or might be queued until
 * a connection request is available.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Unstable
@VertxGen
public interface HttpClientConnection extends HttpConnection, HttpClient {

  /**
   * @return the number of active request/response (streams)
   */
  long activeStreams();

  /**
   * @return the max number of concurrent active streams this connection can handle
   */
  long maxActiveStreams();

  @Override
  default Future<Void> shutdown() {
    return HttpConnection.super.shutdown();
  }

  @Override
  Future<Void> shutdown(long timeout, TimeUnit unit);

  @Override
  default Future<Void> close() {
    return HttpConnection.super.close();
  }
}
