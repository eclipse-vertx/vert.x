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
package io.vertx.core.net.endpoint;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.annotations.VertxGen;

import java.util.List;
import java.util.function.Predicate;

@Unstable
@VertxGen
public interface Endpoint {

  /**
   * The default view, accepting any server.
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  Predicate<ServerEndpoint> DEFAULT_VIEW = server -> true;

  /**
   * The servers capable of serving requests for this endpoint.
   */
  List<ServerEndpoint> servers();

  /**
   * Select a server.
   *
   * @return the selected server
   */
  default ServerEndpoint selectServer() {
    return selectServer(DEFAULT_VIEW);
  }

  /**
   * Select a server.
   *
   * @return the selected server
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default ServerEndpoint selectServer(Predicate<ServerEndpoint> filter) {
    return selectServer(filter, null);
  }

  /**
   * Select a node, using a routing {@code key}
   *
   * @param key the routing key
   * @return the selected server
   */
  default ServerEndpoint selectServer(String key) {
    return selectServer(DEFAULT_VIEW, key);
  }

  /**
   * Select a node, using a routing {@code key}
   *
   * @param key the routing key
   * @param filter the view filter
   * @return the selected server
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  ServerEndpoint selectServer(Predicate<ServerEndpoint> filter, String key);

}
