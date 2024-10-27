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
 * Select the most appropriate server among the list of servers of an endpoint.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Unstable
public interface ServerSelector {

  /**
   * Selects a server.
   *
   * @return the selected index or {@code -1} if selection could not be achieved
   */
  int select();

  /**
   * Select a server using a routing {@code key}.
   *
   * @param key a key used for routing in sticky strategies
   * @return the selected index or {@code -1} if selection could not be achieved
   */
  default int select(String key) {
    return select();
  }
}
