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
import io.vertx.core.spi.loadbalancing.Endpoint;
import io.vertx.core.spi.loadbalancing.EndpointSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;

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
      if (endpoints.isEmpty()) {
        return -1;
      }
      int next = idx.getAndIncrement();
      return next % endpoints.size();
    };
  };

  /**
   * Least request load balancer.
   */
  LoadBalancer LEAST_REQUESTS = () -> new EndpointSelector() {
    @Override
    public int selectEndpoint(List<? extends Endpoint<?>> endpoints) {
      int numberOfRequests = Integer.MAX_VALUE;
      int selected = -1;
      int idx = 0;
      for (Endpoint<?> endpoint : endpoints) {
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
    public <E> Endpoint<E> endpointOf(E endpoint) {
      return new DefaultEndpointMetrics<>(endpoint);
    }
  };

  /**
   * Random load balancer.
   */
  LoadBalancer RANDOM = () -> {
    return (EndpointSelector) endpoints -> {
      if (endpoints.isEmpty()) {
        return -1;
      }
      return ThreadLocalRandom.current().nextInt(endpoints.size());
    };
  };

  /**
   * Power of two choices load balancer.
   */
  LoadBalancer POWER_OF_TWO_CHOICES = () -> new EndpointSelector() {
    @Override
    public int selectEndpoint(List<? extends Endpoint<?>> endpoints) {
      if (endpoints.isEmpty()) {
        return -1;
      } else if (endpoints.size() == 1) {
        return 0;
      }
      int i1 = ThreadLocalRandom.current().nextInt(endpoints.size());
      int i2 = ThreadLocalRandom.current().nextInt(endpoints.size());
      while (i2 == i1) {
        i2 = ThreadLocalRandom.current().nextInt(endpoints.size());
      }
      if (((DefaultEndpointMetrics<?>) endpoints.get(i1)).numberOfInflightRequests() < ((DefaultEndpointMetrics<?>) endpoints.get(i2)).numberOfInflightRequests()) {
        return i1;
      }
      return i2;
    }

    @Override
    public <E> Endpoint<E> endpointOf(E endpoint) {
      return new DefaultEndpointMetrics<>(endpoint);
    }
  };

  /**
   * Create a stateful endpoint selector.
   * @return the selector
   */
  EndpointSelector selector();

}
