/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.spi.resolver.address;

/**
 * A builder of list of endpoints.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface EndpointListBuilder<L, E> {

  /**
   * Add an endpoint with its associated {@code key}
   * @param endpoint the endpoint
   * @param key the key
   * @return the next builder to be used, it might return a new instance
   */
  EndpointListBuilder<L, E> addEndpoint(E endpoint, String key);

  /**
   * Like {@link #addEndpoint(Object, String)} with a default key.
   */
  default EndpointListBuilder<L, E> addEndpoint(E endpoint) {
    return addEndpoint(endpoint, "" + System.identityHashCode(endpoint));
  }

  /**
   * @return the list
   */
  L build();

}
