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
package io.vertx.core.spi.loadbalancing;

import java.util.List;

/**
 * Holds the state for performing load balancing.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface EndpointSelector {

  /**
   * Select an endpoint among the provided list.
   *
   * @param endpoints the endpoint
   * @return the selected index or {@code -1} if selection could not be achieved
   */
  int selectEndpoint(List<? extends Endpoint<?>> endpoints);

  /**
   * Create a load balancer endpoint view for the given generic {@code endpoint}
   * @param endpoint
   * @return
   * @param <E>
   */
  default <E> Endpoint<E> endpointOf(E endpoint) {
    EndpointMetrics<?> metrics = new EndpointMetrics<>() {
    };
    return new Endpoint<>() {
      @Override
      public E endpoint() {
        return endpoint;
      }
      @Override
      public EndpointMetrics<?> metrics() {
        return metrics;
      }
    };
  }
}
