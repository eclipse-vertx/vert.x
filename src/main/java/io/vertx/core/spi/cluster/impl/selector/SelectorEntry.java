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
    Map<String, Weight> weights = computeWeights(nodeIds);
    RoundRobinSelector selector;
    if (isEvenlyDistributed(weights)) {
      selector = new SimpleRoundRobinSelector(new ArrayList<>(weights.keySet()));
    } else {
      selector = new WeightedRoundRobinSelector(weights);
    }
    return new SelectorEntry(selector, selectorPromise, counter);
  }

  private Map<String, Weight> computeWeights(List<String> nodeIds) {
    Map<String, Weight> weights = new HashMap<>();
    for (String nodeId : nodeIds) {
      weights.compute(nodeId, (s, weight) -> weight == null ? new Weight(0) : weight.increment());
    }
    return weights;
  }

  private boolean isEvenlyDistributed(Map<String, Weight> weights) {
    if (weights.size() > 1) {
      Weight previous = null;
      for (Weight weight : weights.values()) {
        if (previous != null && previous.value() != weight.value()) {
          return false;
        }
        previous = weight;
      }
    }
    return true;
  }

  boolean shouldInitialize() {
    return counter == 0;
  }

  boolean isNotReady() {
    return selector == null;
  }
}
