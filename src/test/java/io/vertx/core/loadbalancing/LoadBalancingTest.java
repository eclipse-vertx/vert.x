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

import io.vertx.core.spi.loadbalancing.Endpoint;
import io.vertx.core.spi.loadbalancing.EndpointSelector;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoadBalancingTest {

  @Test
  public void testRoundRobin() throws Exception {
    EndpointSelector selector = LoadBalancer.ROUND_ROBIN.selector();
    Endpoint<?> e1 = selector.endpointOf(null);
    Endpoint<?> e2 = selector.endpointOf(null);
    Endpoint<?> e3 = selector.endpointOf(null);
    List<Endpoint<?>> metrics = Arrays.asList(e1, e2, e3);
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
    List<Endpoint<?>> metrics = new ArrayList<>();
    int num = 6;
    for (int i = 0;i < num;i++) {
      metrics.add(selector.endpointOf(null));
    }
    for (int i = 0;i < metrics.size();i++) {
      if (i != 2) {
        metrics.get(i).metrics().initiateRequest();
      }
    }
    assertEquals(2, selector.selectEndpoint(metrics));
  }

  @Test
  public void testRandom() throws Exception {
    EndpointSelector selector = LoadBalancer.RANDOM.selector();
    List<Endpoint<?>> metrics = new ArrayList<>();
    int num = 6;
    for (int i = 0;i < num;i++) {
      metrics.add(selector.endpointOf(null));
    }
    for (int i = 0; i < 1000; i++) {
      int selectedIndex = selector.selectEndpoint(metrics);
      assertTrue(selectedIndex >= 0 && selectedIndex < metrics.size());
    }
  }

  @Test
  public void testPowerOfTwoChoices() throws Exception {
    EndpointSelector selector = LoadBalancer.POWER_OF_TWO_CHOICES.selector();
    for (int i = 0; i < 1000; i++) {
      Endpoint<?> e1 = selector.endpointOf(null);
      Endpoint<?> e2 = selector.endpointOf(null);
      List<Endpoint<?>> metrics = Arrays.asList(e1, e2);
      e2.metrics().initiateRequest();
      assertEquals(0, selector.selectEndpoint(metrics));
    }

    List<Endpoint<?>> metrics = new ArrayList<>();
    int num = 6;
    for (int i = 0;i < num;i++) {
      metrics.add(selector.endpointOf(null));
    }
    for (int i = 0; i < 1000; i++) {
      int selectedIndex = selector.selectEndpoint(metrics);
      assertTrue(selectedIndex >= 0 && selectedIndex < metrics.size());
    }
  }
}

