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
package io.vertx.core.spi.loadbalancing;

/**
 * The view of an endpoint by the load balancing policy, it actually wraps an actual endpoint, the main purpose
 * of this is to attach metrics to an endpoint that can be managed by the resolver.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @param <E> the endpoint type
 */
public interface Endpoint<E> {

  /**
   * @return the key of the endpoint to be hashed by hashing load balancer
   */
  String key();

  /**
   * @return the endpoint measured by these metrics.
   */
  E endpoint();

  /**
   * @return the metrics for measuring the usage of the endpoint
   */
  EndpointMetrics<?> metrics();

}
