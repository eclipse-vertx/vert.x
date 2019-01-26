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
package io.vertx.core.spi.tracing;

import io.vertx.core.Context;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface VertxTracer<I, O> {

  /**
   * Signal a request has been received and will be processed.
   *
   * @param context the context data attached to the request
   * @param request the request object
   * @param operation the request operation
   * @param headers a read-only view of the request headers
   * @param tags the request tags
   * @return the request trace
   */
  default I receiveRequest(Context context, Object request, String operation, Iterable<Map.Entry<String, String>> headers, Iterable<Map.Entry<String, String>> tags) {
    return null;
  }

  /**
   * Signal the response is sent.
   *
   * @param context the context data attached to the request
   * @param response the response sent
   * @param payload the payload returned by {@link #receiveRequest}
   * @param failure the failure when not {@code null}
   * @param tags the response tags
   */
  default void sendResponse(Context context, Object response, I payload, Throwable failure, Iterable<Map.Entry<String, String>> tags) {
  }

  /**
   * Signal a request is sent.
   *
   * @param context the context data attached to the request
   * @param request the request object
   * @param operation the request operation
   * @param headers a write only-view of the request headers
   * @param tags the request tags
   * @return the request trace
   */
  default O sendRequest(Context context, Object request, String operation, BiConsumer<String, String> headers, Iterable<Map.Entry<String, String>> tags) {
    return null;
  }

  /**
   * Signal a response has been received.
   *
   * @param context the context data attached to the request
   * @param response the response sent
   * @param payload the payload returned by {@link #sendRequest}
   * @param failure the failure when not {@code null}
   * @param tags the response tags
   */
  default void receiveResponse(Context context, Object response, O payload, Throwable failure, Iterable<Map.Entry<String, String>> tags) {
  }

  /**
   * Close the tracer.
   */
  default void close() {
  }
}
