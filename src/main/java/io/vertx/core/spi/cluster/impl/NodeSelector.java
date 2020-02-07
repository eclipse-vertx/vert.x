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

package io.vertx.core.spi.cluster.impl;

import io.vertx.core.eventbus.Message;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.RegistrationInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toCollection;

/**
 * @author Thomas Segismont
 */
class NodeSelector {

  static final NodeSelector EMPTY_SELECTOR = new NodeSelector(Collections.emptyList());

  private final List<NodeInfo> accessible;
  private final List<NodeInfo> distinct;
  private final AtomicInteger index = new AtomicInteger(0);

  private NodeSelector(List<NodeInfo> accessible) {
    this.accessible = accessible;
    if (accessible.isEmpty()) {
      distinct = Collections.emptyList();
    } else {
      distinct = accessible.stream()
        .distinct()
        .collect(collectingAndThen(toCollection(ArrayList::new), Collections::unmodifiableList));
    }
  }

  public static NodeSelector create(NodeInfo nodeInfo, List<RegistrationInfo> registrationInfos) {
    List<NodeInfo> nodeInfos = registrationInfos.stream()
      .filter(registrationInfo -> isNodeAccessible(nodeInfo, registrationInfo))
      .map(RegistrationInfo::getNodeInfo)
      .collect(Collectors.toList());
    return !nodeInfos.isEmpty() ? new NodeSelector(nodeInfos) : NodeSelector.EMPTY_SELECTOR;
  }

  private static boolean isNodeAccessible(NodeInfo nodeInfo, RegistrationInfo registrationInfo) {
    return !registrationInfo.isLocalOnly() || registrationInfo.getNodeInfo().equals(nodeInfo);
  }

  List<NodeInfo> selectNodes(Message<?> message) {
    if (accessible.isEmpty()) {
      return Collections.emptyList();
    }
    if (message.isSend()) {
      return Collections.singletonList(accessible.get(index.incrementAndGet() % accessible.size()));
    }
    return distinct;
  }
}
