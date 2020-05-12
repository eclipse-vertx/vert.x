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

import io.vertx.core.Promise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Thomas Segismont
 */
class SelectorEntry {

  final RoundRobinSelector selector;
  final Promise<RoundRobinSelector> selectorPromise;
  final int counter;

  SelectorEntry() {
    selector = null;
    selectorPromise = Promise.promise();
    counter = 0;
  }

  private SelectorEntry(RoundRobinSelector selector, Promise<RoundRobinSelector> selectorPromise, int counter) {
    this.selector = selector;
    this.selectorPromise = selectorPromise;
    this.counter = counter;
  }

  SelectorEntry increment() {
    return new SelectorEntry(null, selectorPromise, counter + 1);
  }

  SelectorEntry data(List<String> nodeIds) {
    if (nodeIds == null || nodeIds.isEmpty()) {
      return null;
    }
    Map<String, Integer> weights = computeWeights(nodeIds);
    RoundRobinSelector selector;
    if (isEvenlyDistributed(weights)) {
      selector = new SimpleRoundRobinSelector(new ArrayList<>(weights.keySet()));
    } else {
      selector = new WeightedRoundRobinSelector(weights);
    }
    return new SelectorEntry(selector, selectorPromise, counter);
  }

  private Map<String, Integer> computeWeights(List<String> nodeIds) {
    Map<String, Integer> weights = new HashMap<>();
    for (String nodeId : nodeIds) {
      weights.merge(nodeId, 1, Math::addExact);
    }
    return weights;
  }

  private boolean isEvenlyDistributed(Map<String, Integer> weights) {
    return weights.values().stream().distinct().count() == 1;
  }

  boolean shouldInitialize() {
    return counter == 0;
  }

  boolean isNotReady() {
    return selector == null;
  }
}
