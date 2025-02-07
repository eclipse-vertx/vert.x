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

package io.vertx.core.eventbus.impl.clustered.selector;

import java.util.Collections;
import java.util.List;

/**
 * @author Thomas Segismont
 */
public class SimpleRoundRobinSelector implements RoundRobinSelector {

  private final List<String> nodeIds;
  private final Index index;

  public SimpleRoundRobinSelector(List<String> nodeIds) {
    if (nodeIds.size() > 1) {
      this.nodeIds = Collections.unmodifiableList(nodeIds);
      index = new Index(nodeIds.size());
    } else {
      this.nodeIds = Collections.singletonList(nodeIds.get(0));
      index = null;
    }
  }

  @Override
  public String selectForSend() {
    if (index == null) return nodeIds.get(0);
    return nodeIds.get(index.nextVal());
  }

  @Override
  public Iterable<String> selectForPublish() {
    return nodeIds;
  }
}
