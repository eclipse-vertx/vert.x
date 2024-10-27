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
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.net.Address;

/**
 *  A resolver for endpoints.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Unstable
@VertxGen
public interface EndpointResolver {

  /**
   * Resolver an endpoint for the specified {@code address}
   * @param address the address to lookup
   * @return the endpoint lookup result
   */
  Future<Endpoint> resolveEndpoint(Address address);

}
