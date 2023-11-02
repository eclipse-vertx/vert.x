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
package io.vertx.core.loadbalancing;

import io.vertx.core.spi.loadbalancing.EndpointMetrics;
import io.vertx.core.spi.loadbalancing.EndpointSelector;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LoadBalancingTest {

  @Test
  public void testRoundRobin() throws Exception {
    EndpointSelector selector = LoadBalancer.ROUND_ROBIN.selector();
    EndpointMetrics<?> e1 = selector.endpointMetrics();
    EndpointMetrics<?> e2 = selector.endpointMetrics();
    EndpointMetrics<?> e3 = selector.endpointMetrics();
    List<EndpointMetrics<?>> metrics = Arrays.asList(e1, e2, e3);
    assertEquals(0, selector.selectEndpoint(metrics));
    assertEquals(1, selector.selectEndpoint(metrics));
    assertEquals(2, selector.selectEndpoint(metrics));
    assertEquals(0, selector.selectEndpoint(metrics));
    assertEquals(1, selector.selectEndpoint(metrics));
    assertEquals(2, selector.selectEndpoint(metrics));
  }

  @Test
  public void testLeastRequests() throws Exception {
    EndpointSelector selector = LoadBalancer.LEAST_REQUESTS.selector();
    List<EndpointMetrics<?>> metrics = new ArrayList<>();
    int num = 6;
    for (int i = 0;i < num;i++) {
      metrics.add(selector.endpointMetrics());
    }
    for (int i = 0;i < metrics.size();i++) {
      if (i != 2) {
        metrics.get(i).initiateRequest();
      }
    }
    assertEquals(2, selector.selectEndpoint(metrics));
  }
}
