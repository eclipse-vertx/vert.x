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

import io.vertx.core.Promise;

import java.util.List;

/**
 * @author Thomas Segismont
 */
class SelectorEntry {

  final Selector selector;
  final Promise<Selector> selectorPromise;
  final int counter;

  SelectorEntry() {
    selector = null;
    selectorPromise = Promise.promise();
    counter = 0;
  }

  private SelectorEntry(Selector selector, Promise<Selector> selectorPromise, int counter) {
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
    return new SelectorEntry(Selector.create(nodeIds), selectorPromise, counter);
  }

  boolean shouldInitialize() {
    return counter == 0;
  }

  boolean isNotReady() {
    return selector == null;
  }
}
