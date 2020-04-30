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

import io.vertx.core.impl.Arguments;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

/**
 * @author Thomas Segismont
 */
public class Selector {

  static final Selector EMPTY = new Selector(Collections.emptyList(), Collections.emptyList(), null);

  private final List<String> accessible;
  private final List<String> distinct;
  private final AtomicInteger index;

  private Selector(List<String> accessible, List<String> distinct, AtomicInteger index) {
    this.accessible = accessible;
    this.distinct = distinct;
    this.index = index;
  }

  public static Selector create(List<String> nodeIds) {
    Arguments.require(nodeIds != null && !nodeIds.isEmpty(), "nodeIds is empty");
    List<String> distinct = nodeIds.stream()
      .distinct()
      .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    return new Selector(distinct.size() == nodeIds.size() ? distinct : nodeIds, distinct, new AtomicInteger());
  }

  public String selectForSend() {
    return accessible.isEmpty() ? null : accessible.get(index.incrementAndGet() % accessible.size());
  }

  public Iterable<String> selectForPublish() {
    return distinct;
  }
}
