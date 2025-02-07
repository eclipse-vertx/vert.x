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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

/**
 * @author Thomas Segismont
 */
class Index implements IntUnaryOperator {

  private final int max;
  private final AtomicInteger idx = new AtomicInteger(0);

  Index(int max) {
    this.max = max;
  }

  int nextVal() {
    return idx.getAndUpdate(this);
  }

  @Override
  public int applyAsInt(int i) {
    return i == max - 1 ? 0 : i + 1;
  }
}
