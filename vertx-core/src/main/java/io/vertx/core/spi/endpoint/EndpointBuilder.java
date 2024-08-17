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
package io.vertx.core.spi.endpoint;

/**
 * A builder for an endpoint.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface EndpointBuilder<E, S> {

  /**
   * Add a {@code server} with its associated {@code key}
   * @param server the server
   * @param key the key
   * @return the next builder to be used, it might return a new instance
   */
  EndpointBuilder<E, S> addServer(S server, String key);

  /**
   * Like {@link #addServer(Object, String)} with a default key.
   */
  default EndpointBuilder<E, S> addServer(S server) {
    return addServer(server, "" + System.identityHashCode(server));
  }

  /**
   * @return the endpoint
   */
  E build();

}
