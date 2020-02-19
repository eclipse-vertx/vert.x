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
import io.vertx.core.spi.cluster.RegistrationInfo;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

/**
 * @author Thomas Segismont
 */
class NodeSelector {

  static final NodeSelector EMPTY_SELECTOR = new NodeSelector(Collections.emptyList());

  private final List<String> accessible;
  private final List<String> distinct;
  private final AtomicInteger index = new AtomicInteger(0);

  private NodeSelector(List<String> accessible) {
    this.accessible = accessible;
    if (accessible.isEmpty()) {
      distinct = Collections.emptyList();
    } else {
      distinct = accessible.stream()
        .distinct()
        .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }
  }

  public static NodeSelector create(String nodeId, List<RegistrationInfo> registrationInfos) {
    List<String> nodeIds = registrationInfos.stream()
      .filter(registrationInfo -> isNodeAccessible(nodeId, registrationInfo))
      .map(RegistrationInfo::getNodeId)
      .collect(toList());
    return !nodeIds.isEmpty() ? new NodeSelector(nodeIds) : NodeSelector.EMPTY_SELECTOR;
  }

  private static boolean isNodeAccessible(String nodeId, RegistrationInfo registrationInfo) {
    return !registrationInfo.isLocalOnly() || registrationInfo.getNodeId().equals(nodeId);
  }

  List<String> selectNodes(Message<?> message) {
    if (accessible.isEmpty()) {
      return Collections.emptyList();
    }
    if (message.isSend()) {
      return Collections.singletonList(accessible.get(index.incrementAndGet() % accessible.size()));
    }
    return distinct;
  }
}
