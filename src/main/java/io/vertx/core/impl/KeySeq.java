/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class KeySeq {

  private static final AtomicInteger seq = new AtomicInteger();

  /**
   * Hook for testing purposes
   */
  static void reset() {
    seq.set((0));
  }

  static int get() {
    return seq.get();
  }

  static int next() {
    return seq.getAndIncrement();
  }
}
