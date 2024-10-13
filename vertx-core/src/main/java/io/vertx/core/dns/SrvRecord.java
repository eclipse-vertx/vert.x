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

package io.vertx.core.dns;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Nullable;

/**
 * Represent a Service-Record (SRV) which was resolved for a domain.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@DataObject
public interface SrvRecord {

  /**
   * Returns the priority for this service record.
   */
  int priority();

  /**
   * Returns the record time to live
   */
  long ttl();

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
