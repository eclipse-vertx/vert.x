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

package io.vertx.core.spi.cluster.impl.selectors;

import java.util.*;

/**
 * @author Thomas Segismont
 */
class WeightedRoundRobinSelector implements Selector {

  private final List<String> uniqueIds;
  private final TreeMap<Integer, Integer> offsets = new TreeMap<>();
  private final Index index;

  WeightedRoundRobinSelector(Map<String, Integer> weights) {
    List<String> uniqueIds = new ArrayList<>(weights.size());
    List<Map.Entry<String, Integer>> sorted = new ArrayList<>(weights.entrySet());
    sorted.sort(Map.Entry.comparingByValue());
    int totalWeight = 0;
    int increment, limit;
    for (int i = 0; i < sorted.size(); i++) {
      Map.Entry<String, Integer> current = sorted.get(i);
      uniqueIds.add(current.getKey());
      int weight = current.getValue();
      totalWeight += weight;
      if (i < sorted.size() - 1) {
        increment = weight - (i == 0 ? 0 : sorted.get(i - 1).getValue());
        limit = (i == 0 ? 0 : offsets.lastKey()) + (weights.size() - i) * increment;
        offsets.put(limit, i + 1);
      }
    }
    this.uniqueIds = Collections.unmodifiableList(uniqueIds);
    index = new Index(totalWeight);
  }

  @Override
  public String selectForSend() {
    int idx = index.nextVal();
    Map.Entry<Integer, Integer> entry = offsets.floorEntry(idx);
    if (entry == null) return uniqueIds.get(idx % uniqueIds.size());
    int offset = entry.getValue();
    if (offset == uniqueIds.size() - 1) return uniqueIds.get(offset);
    return uniqueIds.get(offset + idx % (uniqueIds.size() - offset));
  }

  @Override
  public Iterable<String> selectForPublish() {
    return uniqueIds;
  }
}
