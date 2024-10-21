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
package io.vertx.tests.endpoint;

import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.endpoint.*;
import io.vertx.test.core.TestUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.vertx.core.net.endpoint.LoadBalancer.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoadBalancingTest {

  ServerEndpoint endpointOf(LoadBalancer loadBalancer) {
    InteractionMetrics<?> metrics = loadBalancer.newMetrics();
    return new ServerEndpoint() {
      @Override
      public SocketAddress address() {
        return null;
      }
      @Override
      public String key() {
        return "";
      }
      @Override
      public Object unwrap() {
        return null;
      }
      @Override
      public InteractionMetrics<?> metrics() {
        return metrics;
      }
      @Override
      public ServerInteraction newInteraction() {
        return null;
      }
    };
  }

  @Test
  public void testRoundRobin() throws Exception {
    ServerEndpoint e1 = endpointOf(ROUND_ROBIN);
    ServerEndpoint e2 = endpointOf(ROUND_ROBIN);
    ServerEndpoint e3 = endpointOf(ROUND_ROBIN);
    List<ServerEndpoint> metrics = Arrays.asList(e1, e2, e3);
    ServerSelector selector = ROUND_ROBIN.selector(metrics);
    assertEquals(0, selector.select());
    assertEquals(1, selector.select());
    assertEquals(2, selector.select());
    assertEquals(0, selector.select());
    assertEquals(1, selector.select());
    assertEquals(2, selector.select());
  }

  @Test
  public void testLeastRequests() throws Exception {
    List<ServerEndpoint> metrics = new ArrayList<>();
    int num = 6;
    for (int i = 0;i < num;i++) {
      metrics.add(endpointOf(LEAST_REQUESTS));
    }
    for (int i = 0;i < metrics.size();i++) {
      if (i != 2) {
        metrics.get(i).metrics().initiateRequest();
      }
    }
    ServerSelector selector = LoadBalancer.LEAST_REQUESTS.selector(metrics);
    assertEquals(2, selector.select());
  }

  @Test
  public void testRandom() throws Exception {
    List<ServerEndpoint> metrics = new ArrayList<>();
    int num = 6;
    for (int i = 0;i < num;i++) {
      metrics.add(endpointOf(RANDOM));
    }
    ServerSelector selector = LoadBalancer.RANDOM.selector(metrics);
    for (int i = 0; i < 1000; i++) {
      int selectedIndex = selector.select();
      assertTrue(selectedIndex >= 0 && selectedIndex < metrics.size());
    }
  }

  @Test
  public void testPowerOfTwoChoices() throws Exception {
    for (int i = 0; i < 1000; i++) {
      ServerEndpoint e1 = endpointOf(POWER_OF_TWO_CHOICES);
      ServerEndpoint e2 = endpointOf(POWER_OF_TWO_CHOICES);
      List<ServerEndpoint> metrics = Arrays.asList(e1, e2);
      e2.metrics().initiateRequest();
      ServerSelector selector = LoadBalancer.POWER_OF_TWO_CHOICES.selector(metrics);
      assertEquals(0, selector.select());
    }

    List<ServerEndpoint> metrics = new ArrayList<>();
    int num = 6;
    for (int i = 0;i < num;i++) {
      metrics.add(endpointOf(POWER_OF_TWO_CHOICES));
    }
    ServerSelector selector = LoadBalancer.POWER_OF_TWO_CHOICES.selector(metrics);
    for (int i = 0; i < 1000; i++) {
      int selectedIndex = selector.select();
      assertTrue(selectedIndex >= 0 && selectedIndex < metrics.size());
    }
  }

  @Test
  public void testConsistentHashing() {
    ServerEndpoint e1 = endpointOf(CONSISTENT_HASHING);
    ServerEndpoint e2 = endpointOf(CONSISTENT_HASHING);
    ServerEndpoint e3 = endpointOf(CONSISTENT_HASHING);
    ServerSelector selector = LoadBalancer.CONSISTENT_HASHING.selector(Arrays.asList(e1, e2, e3));
    int num = 100;
    List<String> ids = new ArrayList<>(num);
    for (int i = 0;i < num;i++) {
      ids.add(TestUtils.randomAlphaString(40));
    }
    for (int i = 0;i < num;i++) {
      String id = ids.get(i);
      int idx = selector.select(id);
      assertTrue(idx >= 0 && idx < 4);
      for (int j = 0;j < 16;j++) {
        assertEquals(idx, selector.select(id));
      }
    }
    // Fallback on random selector
    int bitset = 0;
    while (bitset != 7) {
      int res = selector.select();
      assertTrue(res >= 0 && res < 4);
      bitset |= 1 << res;
    }
  }
}
