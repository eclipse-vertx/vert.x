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

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.net.SocketAddress;

/**
 * A physical server of an endpoint.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Unstable
@VertxGen
public interface ServerEndpoint {

  /**
   * @return the node key for hashing strategies
   */
  String key();

  /**
   * @return the server socket address
   */
  SocketAddress address();

  /**
   * Initiate a request/response interaction with the endpoint represented by this node, the returned interaction gathers statistics.
   *
   * @return the request
   */
  ServerInteraction newInteraction();

  // Should be private somehow
  @GenIgnore
  InteractionMetrics<?> metrics();

  @GenIgnore
  Object unwrap();

}
