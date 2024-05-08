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
import io.vertx.core.spi.loadbalancing.EndpointMetrics;
import io.vertx.core.spi.loadbalancing.EndpointSelector;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A load balancer.
 *
 * A load balancer is stateless besides the configuration part. Effective load balancing can be achieved with
 * {@link #selector(List)} which creates a stateful {@link EndpointSelector} implementing the load balancing policy.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface LoadBalancer {

  /**
   * Create a load balancer endpoint view for the given generic {@code endpoint}
   *
   * @param endpoint the endpoint to wrap
   * @param id the id of the endpoint
   * @return the wrapped endpoint
   */
  default <E> Endpoint<E> endpointOf(E endpoint, String id) {
    EndpointMetrics<?> metrics = new EndpointMetrics<>() {
    };
    return new Endpoint<>() {
      @Override
      public String key() {
        return id;
      }
      @Override
      public E endpoint() {
        return endpoint;
      }
      @Override
      public EndpointMetrics<?> metrics() {
        return metrics;
      }
    };
  }

  /**
   * Simple round-robin load balancer.
   */
  LoadBalancer ROUND_ROBIN = endpoints -> {
    AtomicInteger idx = new AtomicInteger();
    return (EndpointSelector) () -> {
      if (endpoints.isEmpty()) {
        return -1;
      }
      int next = idx.getAndIncrement();
      return next % endpoints.size();
    };
  };

  /**
   * Least requests load balancer.
   */
  LoadBalancer LEAST_REQUESTS = (LoadBalancer2) endpoints -> () -> {
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
  };

  /**
   * Random load balancer.
   */
  LoadBalancer RANDOM = (LoadBalancer2) endpoints -> () -> {
    if (endpoints.isEmpty()) {
      return -1;
    }
    return ThreadLocalRandom.current().nextInt(endpoints.size());
  };

  /**
   * Power of two choices load balancer.
   */
  LoadBalancer POWER_OF_TWO_CHOICES = (LoadBalancer2) endpoints -> (EndpointSelector) () -> {
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
  };

  /**
   * Consistent hashing load balancer with 4 virtual nodes, falling back to a random load balancer.
   */
  LoadBalancer CONSISTENT_HASHING = consistentHashing(4, RANDOM);

  /**
   * Sticky load balancer that uses consistent hashing based on a client provided routing key, defaulting to the {@code fallback}
   * load balancer when no routing key is provided.
   *
   * @param numberOfVirtualNodes the number of virtual nodes
   * @param fallback the fallback load balancer for non-sticky requests
   * @return the load balancer
   */
  static LoadBalancer consistentHashing(int numberOfVirtualNodes, LoadBalancer fallback) {
    return endpoints -> {
      EndpointSelector fallbackSelector = fallback.selector(endpoints);
      return new ConsistentHashingSelector(endpoints, numberOfVirtualNodes, fallbackSelector);
    };
  }

  /**
   * Create a stateful endpoint selector.
   * @return the selector
   */
  EndpointSelector selector(List<? extends Endpoint<?>> endpoints);

}
