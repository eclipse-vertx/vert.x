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
import io.vertx.test.core.TestUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoadBalancingTest {

  @Test
  public void testRoundRobin() throws Exception {
    Endpoint<?> e1 = LoadBalancer.ROUND_ROBIN.endpointOf(null, "");
    Endpoint<?> e2 = LoadBalancer.ROUND_ROBIN.endpointOf(null, "");
    Endpoint<?> e3 = LoadBalancer.ROUND_ROBIN.endpointOf(null, "");
    List<Endpoint<?>> metrics = Arrays.asList(e1, e2, e3);
    EndpointSelector selector = LoadBalancer.ROUND_ROBIN.selector(metrics);
    assertEquals(0, selector.selectEndpoint());
    assertEquals(1, selector.selectEndpoint());
    assertEquals(2, selector.selectEndpoint());
    assertEquals(0, selector.selectEndpoint());
    assertEquals(1, selector.selectEndpoint());
    assertEquals(2, selector.selectEndpoint());
  }

  @Test
  public void testLeastRequests() throws Exception {
    List<Endpoint<?>> metrics = new ArrayList<>();
    int num = 6;
    for (int i = 0;i < num;i++) {
      metrics.add(LoadBalancer.LEAST_REQUESTS.endpointOf(null, ""));
    }
    for (int i = 0;i < metrics.size();i++) {
      if (i != 2) {
        metrics.get(i).metrics().initiateRequest();
      }
    }
    EndpointSelector selector = LoadBalancer.LEAST_REQUESTS.selector(metrics);
    assertEquals(2, selector.selectEndpoint());
  }

  @Test
  public void testRandom() throws Exception {
    List<Endpoint<?>> metrics = new ArrayList<>();
    int num = 6;
    for (int i = 0;i < num;i++) {
      metrics.add(LoadBalancer.RANDOM.endpointOf(null, ""));
    }
    EndpointSelector selector = LoadBalancer.RANDOM.selector(metrics);
    for (int i = 0; i < 1000; i++) {
      int selectedIndex = selector.selectEndpoint();
      assertTrue(selectedIndex >= 0 && selectedIndex < metrics.size());
    }
  }

  @Test
  public void testPowerOfTwoChoices() throws Exception {
    for (int i = 0; i < 1000; i++) {
      Endpoint<?> e1 = LoadBalancer.POWER_OF_TWO_CHOICES.endpointOf(null, "");
      Endpoint<?> e2 = LoadBalancer.POWER_OF_TWO_CHOICES.endpointOf(null, "");
      List<Endpoint<?>> metrics = Arrays.asList(e1, e2);
      e2.metrics().initiateRequest();
      EndpointSelector selector = LoadBalancer.POWER_OF_TWO_CHOICES.selector(metrics);
      assertEquals(0, selector.selectEndpoint());
    }

    List<Endpoint<?>> metrics = new ArrayList<>();
    int num = 6;
    for (int i = 0;i < num;i++) {
      metrics.add(LoadBalancer.POWER_OF_TWO_CHOICES.endpointOf(null, ""));
    }
    EndpointSelector selector = LoadBalancer.POWER_OF_TWO_CHOICES.selector(metrics);
    for (int i = 0; i < 1000; i++) {
      int selectedIndex = selector.selectEndpoint();
      assertTrue(selectedIndex >= 0 && selectedIndex < metrics.size());
    }
  }

  @Test
  public void testConsistentHashing() {
    Endpoint<?> e1 = LoadBalancer.CONSISTENT_HASHING.endpointOf(null, "e1");
    Endpoint<?> e2 = LoadBalancer.CONSISTENT_HASHING.endpointOf(null, "e2");
    Endpoint<?> e3 = LoadBalancer.CONSISTENT_HASHING.endpointOf(null, "e3");
    EndpointSelector selector = LoadBalancer.CONSISTENT_HASHING.selector(Arrays.asList(e1, e2, e3));
    int num = 100;
    List<String> ids = new ArrayList<>(num);
    for (int i = 0;i < num;i++) {
      ids.add(TestUtils.randomAlphaString(40));
    }
    for (int i = 0;i < num;i++) {
      String id = ids.get(i);
      int idx = selector.selectEndpoint(id);
      assertTrue(idx >= 0 && idx < 4);
      for (int j = 0;j < 16;j++) {
        assertEquals(idx, selector.selectEndpoint(id));
      }
    }
    // Fallback on random selector
    int bitset = 0;
    while (bitset != 7) {
      int res = selector.selectEndpoint();
      assertTrue(res >= 0 && res < 4);
      bitset |= 1 << res;
    }
  }
}
