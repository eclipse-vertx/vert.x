/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi.cluster.impl.selector;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.runners.Parameterized.Parameters;

/**
 * @author Thomas Segismont
 */
@RunWith(Parameterized.class)
public class WeightedRoundRobinSelectorTest {

  @Parameters
  public static Collection<Object[]> data() {
    List<Object[]> objects = new ArrayList<>();
    Map<String, Integer> map = new HashMap<>();
    map.put("foo", 38);
    map.put("bar", 13);
    objects.add(new Object[]{map});
    map = new HashMap<>();
    map.put("foo", 91);
    map.put("bar", 22);
    map.put("baz", 115);
    objects.add(new Object[]{map});
    map = new HashMap<>();
    map.put("foo", 28);
    map.put("bar", 91);
    map.put("baz", 28);
    map.put("qux", 13);
    map.put("quux", 28);
    objects.add(new Object[]{map});
    return objects;
  }

  private final Map<String, Integer> weights;
  private final int totalWeight;
  private final WeightedRoundRobinSelector selector;

  public WeightedRoundRobinSelectorTest(Map<String, Integer> weights) {
    this.weights = weights;
    totalWeight = weights.values().stream().mapToInt(value -> value).sum();
    selector = new WeightedRoundRobinSelector(weights);
  }

  @Test
  public void testSelectForSend() {
    List<String> list = IntStream.range(0, totalWeight * 10)
      .mapToObj(i -> selector.selectForSend())
      .collect(toList());

    Map<String, Integer> counts = new HashMap<>();
    for (String nodeId : list) {
      assertTrue(weights.containsKey(nodeId));
      counts.merge(nodeId, 1, Math::addExact);
    }

    for (Map.Entry<String, Integer> count : counts.entrySet()) {
      assertEquals(10 * weights.get(count.getKey()), count.getValue().intValue());
    }
  }

  @Test
  public void testSelectForPublish() {
    for (int i = 0; i < 10; i++) {
      Iterable<String> iterable = selector.selectForPublish();
      List<String> list = StreamSupport.stream(iterable.spliterator(), false).collect(toList());
      assertTrue(list.containsAll(weights.keySet()));
      assertTrue(weights.keySet().containsAll(list));
    }
  }
}
