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
package io.vertx.core.loadbalancing;

import io.vertx.core.spi.loadbalancing.DefaultEndpointMetrics;
import io.vertx.core.spi.loadbalancing.EndpointMetrics;
import io.vertx.core.spi.loadbalancing.EndpointSelector;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A load balancer.
 *
 * A load balancer is stateless besides the configuration part. Effective load balancing can be achieved with
 * {@link #selector()} which creates a stateful {@link EndpointSelector} implementing the load balancing policy.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface LoadBalancer {

  /**
   * Simple round-robin load balancer.
   */
  LoadBalancer ROUND_ROBIN = () -> {
    AtomicInteger idx = new AtomicInteger();
    return (EndpointSelector) endpoints -> {
      int next = idx.getAndIncrement();
      return next % endpoints.size();
    };
  };

  /**
   * Least request load balancer.
   */
  LoadBalancer LEAST_REQUESTS = () -> new EndpointSelector() {
    @Override
    public int selectEndpoint(List<EndpointMetrics<?>> endpoints) {
      int numberOfRequests = Integer.MAX_VALUE;
      int selected = -1;
      int idx = 0;
      for (EndpointMetrics<?> endpoint : endpoints) {
        int val = ((DefaultEndpointMetrics)endpoint).numberOfInflightRequests();
        if (val < numberOfRequests) {
          numberOfRequests = val;
          selected = idx;
        }
        idx++;
      }
      return selected;
    }
    @Override
    public EndpointMetrics<?> endpointMetrics() {
      return new DefaultEndpointMetrics();
    }
  };

  /**
   * Create a stateful endpoint selector.
   * @return the selector
   */
  EndpointSelector selector();

}
