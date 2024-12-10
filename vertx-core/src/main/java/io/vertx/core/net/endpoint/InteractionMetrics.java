/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.endpoint;

import io.vertx.codegen.annotations.Unstable;

/**
 * Gather metrics for a request/response interaction with the server, this interface is write-only and used to report
 * usage to build statistics for a load balancing purpose.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Unstable
public interface InteractionMetrics<M> {

  InteractionMetrics<?> INSTANCE = new InteractionMetrics<>() {
  };

  /**
   * Initiate a request
   *
   * @return the request metric
   */
  default M initiateRequest() {
    return null;
  }

  /**
   * Signal the failure of a request/response to the {@code metric}
   * @param metric the request metric
   * @param failure the failure
   */
  default void reportFailure(M metric, Throwable failure) {
  }

  /**
   * Signal the beginning of the request attached to the {@code metric}
   * @param metric the request/response metric
   */
  default void reportRequestBegin(M metric) {
  }

  /**
   * Signal the end of the request attached to the {@code metric}
   * @param metric the request/response metric
   */
  default void reportRequestEnd(M metric) {
  }

  /**
   * Signal the beginning of the response attached to the {@code metric}
   * @param metric the request/response metric
   */
  default void reportResponseBegin(M metric) {
  }

  /**
   * Signal the end of the response attached to the {@code metric}
   * @param metric the request metric
   */
  default void reportResponseEnd(M metric) {
  }
}
