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

/**
 * Holds the state for performing load balancing.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface EndpointSelector {

  /**
   * Select an endpoint among the provided list.
   *
   * @return the selected index or {@code -1} if selection could not be achieved
   */
  int selectEndpoint();

  /**
   * Select an endpoint among the provided list using a routing {@code key}.
   *
   * @param key a key used for routing in sticky strategies
   * @return the selected index or {@code -1} if selection could not be achieved
   */
  default int selectEndpoint(String key) {
    return selectEndpoint();
  }
}
