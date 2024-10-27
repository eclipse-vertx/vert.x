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
import io.vertx.core.net.endpoint.impl.ConsistentHashingSelector;
import io.vertx.core.net.endpoint.impl.NoMetricsLoadBalancer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A load balancer.
 * <p>
 * A load balancer is stateless besides the configuration part. Effective load balancing can be achieved with
 * {@link #selector(List)} which creates a stateful {@link ServerSelector} implementing the load balancing algorithm.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Unstable
public interface LoadBalancer {

  /**
   * @implSpec
   * The default implementation returns a new instance of {@link DefaultInteractionMetrics}.
   *
   * @return a new interaction metrics instance
   */
  default InteractionMetrics<?> newMetrics() {
    return new DefaultInteractionMetrics();
  }

  /**
   * Simple round-robin load balancer.
   */
  LoadBalancer ROUND_ROBIN = (NoMetricsLoadBalancer) servers -> {
    AtomicInteger idx = new AtomicInteger();
    return () -> {
      if (servers.isEmpty()) {
        return -1;
      }
      int next = idx.getAndIncrement();
      return next % servers.size();
    };
  };

  /**
   * Least requests load balancer.
   */
  LoadBalancer LEAST_REQUESTS = servers -> () -> {
    int numberOfRequests = Integer.MAX_VALUE;
    int selected = -1;
    int idx = 0;
    for (ServerEndpoint node : servers) {
      int val = ((DefaultInteractionMetrics)node.metrics()).numberOfInflightRequests();
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
  LoadBalancer RANDOM = (NoMetricsLoadBalancer) servers -> () -> {
    if (servers.isEmpty()) {
      return -1;
    }
    return ThreadLocalRandom.current().nextInt(servers.size());
  };

  /**
   * Power of two choices load balancer.
   */
  LoadBalancer POWER_OF_TWO_CHOICES = servers -> () -> {
    if (servers.isEmpty()) {
      return -1;
    } else if (servers.size() == 1) {
      return 0;
    }
    int i1 = ThreadLocalRandom.current().nextInt(servers.size());
    int i2 = ThreadLocalRandom.current().nextInt(servers.size());
    while (i2 == i1) {
      i2 = ThreadLocalRandom.current().nextInt(servers.size());
    }
    if (((DefaultInteractionMetrics) servers.get(i1).metrics()).numberOfInflightRequests() < ((DefaultInteractionMetrics) servers.get(i2).metrics()).numberOfInflightRequests()) {
      return i1;
    }
    return i2;
  };

  /**
   * Consistent hashing load balancer with 4 virtual servers, falling back to a random load balancer.
   */
  LoadBalancer CONSISTENT_HASHING = consistentHashing(4, RANDOM);

  /**
   * Sticky load balancer that uses consistent hashing based on a client provided routing key, defaulting to the {@code fallback}
   * load balancer when no routing key is provided.
   *
   * @param numberOfVirtualServers the number of virtual servers
   * @param fallback the fallback load balancer for non-sticky requests
   * @return the load balancer
   */
  static LoadBalancer consistentHashing(int numberOfVirtualServers, LoadBalancer fallback) {
    return servers -> {
      ServerSelector fallbackSelector = fallback.selector(servers);
      return new ConsistentHashingSelector(servers, numberOfVirtualServers, fallbackSelector);
    };
  }

  /**
   * Create a stateful endpoint selector.
   *
   * @param listOfServers the list of servers
   * @return the selector
   */
  ServerSelector selector(List<? extends ServerEndpoint> listOfServers);

}
