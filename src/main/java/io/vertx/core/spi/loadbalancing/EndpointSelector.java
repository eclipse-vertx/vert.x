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
   * @return the selected index or {@code -1} if selection coul not be achieved
   */
  int selectEndpoint(List<EndpointMetrics<?>> endpoints);

  /**
   * Create a blank endpoint used to track the usage of an endpoint.
   *
   * @return a new endpoint
   */
  default EndpointMetrics<?> endpointMetrics() {
    return new EndpointMetrics<>() {
    };
  }
}
