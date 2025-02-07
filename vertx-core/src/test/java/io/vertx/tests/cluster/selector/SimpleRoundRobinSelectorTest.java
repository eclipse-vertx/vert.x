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

package io.vertx.tests.cluster.selector;

import io.vertx.core.eventbus.impl.clustered.selector.SimpleRoundRobinSelector;
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
public class SimpleRoundRobinSelectorTest {

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {Collections.singletonList("foo")},
      {Arrays.asList("foo", "bar", "baz")}
    });
  }

  private final List<String> nodeIds;
  private final SimpleRoundRobinSelector selector;

  public SimpleRoundRobinSelectorTest(List<String> nodeIds) {
    this.nodeIds = nodeIds;
    selector = new SimpleRoundRobinSelector(nodeIds);
  }

  @Test
  public void testSelectForSend() {
    List<String> list = IntStream.range(0, nodeIds.size() * 10)
      .mapToObj(i -> selector.selectForSend())
      .collect(toList());

    Map<String, Integer> counts = new HashMap<>();
    for (String nodeId : list) {
      assertTrue(nodeIds.contains(nodeId));
      counts.merge(nodeId, 1, Math::addExact);
    }

    for (int count : counts.values()) {
      assertEquals(10, count);
    }
  }

  @Test
  public void testSelectForPublish() {
    for (int i = 0; i < 10; i++) {
      Iterable<String> iterable = selector.selectForPublish();
      List<String> list = StreamSupport.stream(iterable.spliterator(), false).collect(toList());
      assertTrue(nodeIds.containsAll(list));
      assertTrue(list.containsAll(nodeIds));
    }
  }
}
