/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.cluster.selector;

import io.vertx.core.eventbus.impl.clustered.selector.Weight;
import io.vertx.core.eventbus.impl.clustered.selector.WeightedRoundRobinSelector;
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
    Map<String, Weight> map = new HashMap<>();
    map.put("foo", new Weight(38));
    map.put("bar", new Weight(13));
    objects.add(new Object[]{map});
    map = new HashMap<>();
    map.put("foo", new Weight(91));
    map.put("bar", new Weight(22));
    map.put("baz", new Weight(115));
    objects.add(new Object[]{map});
    map = new HashMap<>();
    map.put("foo", new Weight(28));
    map.put("bar", new Weight(91));
    map.put("baz", new Weight(28));
    map.put("qux", new Weight(13));
    map.put("quux", new Weight(28));
    objects.add(new Object[]{map});
    return objects;
  }

  private final Map<String, Weight> weights;
  private final int totalWeight;
  private final WeightedRoundRobinSelector selector;

  public WeightedRoundRobinSelectorTest(Map<String, Weight> weights) {
    this.weights = weights;
    totalWeight = weights.values().stream().mapToInt(Weight::value).sum();
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
      assertEquals(10 * weights.get(count.getKey()).value(), count.getValue().intValue());
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
