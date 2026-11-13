/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.vertx.codegen.annotations.VertxGen;

/**
 * How a {@link HttpClient} pool selects a connection among the pooled connections with available capacity.
 *
 * @author <a href="mailto:ninadsutrave@gmail.com">Ninad Sutrave</a>
 */
@VertxGen
public enum ConnectionSelection {

  /**
   * Select the connection that received a response most recently.
   */
  LIFO,

  /**
   * Select connections in turn.
   */
  ROUND_ROBIN,

  /**
   * Select the connection with the fewest in-flight requests.
   */
  LEAST_REQUESTS

}
