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

package io.vertx.core.eventbus.impl.clustered.selector;

import java.util.*;

/**
 * @author Thomas Segismont
 */
public class WeightedRoundRobinSelector implements RoundRobinSelector {

  /*
   * Implementation notes.
   *
   * This selector is made of:
   *
   *  - a list of unique node ids, ordered by weight
   *  - an index, ranging from 0 to the total weight (excluded)
   *  - a map of offsets built to determine, for any index value, the subset of unique node ids that can receive messages
   *
   * Consider the following node ids with their weight:
   *
   * +-----+-----+-----+-----+
   * +  a  +  b  +  c  +  d  +
   * +-----+-----+-----+-----+
   * + 15  + 25  + 25  + 35  +
   * +-----+-----+-----+-----+
   *
   * Total weight is 100, index ranges from 0 to 99.
   *
   * Weight increments are:
   *
   * +-----+-----+-----+-----+
   * + 15  + 10  +  0  + 10  +
   * +-----+-----+-----+-----+
   *
   * From 0 to 59 (4 * 15): a,b,c,d can receive messages.
   * From 60 to 89 (3 * 10): b,c,d can receive messages.
   * From 90 to 90 (2 * 0): c,d can receive messages.
   * From 90 to 99 (1 * 10): d can receive messages.
   *
   * Consequently, the following map of offsets can be built: (0 => 0, 60 => 1, 90 => 3)
   * A tree map allows to retrieve efficiently the entry that has the biggest key less than or equal to the current index value.
   *
   * In practice, the first mapping (0 => 0) is not stored as it is superfluous:
   * The tree map lookup will return null for index values between 0 to 59 and in this case the offset is inferred to 0.
   */

  private final List<String> uniqueIds;
  private final TreeMap<Integer, Integer> offsets = new TreeMap<>();
  private final Index index;

  public WeightedRoundRobinSelector(Map<String, Weight> weights) {
    List<String> uniqueIds = new ArrayList<>(weights.size());
    List<Map.Entry<String, Weight>> sorted = new ArrayList<>(weights.entrySet());
    sorted.sort(Map.Entry.comparingByValue());
    int totalWeight = 0;
    int increment, limit;
    for (int i = 0; i < sorted.size(); i++) {
      Map.Entry<String, Weight> current = sorted.get(i);
      uniqueIds.add(current.getKey());
      int weight = current.getValue().value();
      totalWeight += weight;
      if (i < sorted.size() - 1) {
        increment = weight - (i == 0 ? 0 : sorted.get(i - 1).getValue().value());
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
