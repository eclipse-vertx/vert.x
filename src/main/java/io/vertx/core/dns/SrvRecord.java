/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.dns;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;

/**
 * Represent a Service-Record (SRV) which was resolved for a domain.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@VertxGen
public interface SrvRecord {

  /**
   * Returns the priority for this service record.
   */
  int priority();

  /**
   * Returns the weight of this service record.
   */
  int weight();

  /**
   * Returns the port the service is running on.
   */
  int port();

  /**
   * Returns the name for the server being queried.
   */
  String name();

  /**
   * Returns the protocol for the service being queried (i.e. "_tcp").
   */
  String protocol();

  /**
   * Returns the service's name (i.e. "_http").
   */
  String service();

  /**
   * Returns the name of the host for the service.
   */
  @Nullable
  String target();
}
