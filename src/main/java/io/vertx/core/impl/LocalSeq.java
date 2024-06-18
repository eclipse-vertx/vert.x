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

import io.vertx.core.spi.context.storage.ContextLocal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class LocalSeq {

  // 0 : reserved slot for local context map
  private static final List<ContextLocal<?>> locals = new ArrayList<>();

  static {
    reset();
  }

  /**
   * Hook for testing purposes
   */
  static void reset() {
    synchronized (locals) {
      locals.clear();
      locals.add(ContextInternal.LOCAL_MAP);
    }
  }

  static ContextLocal<?>[] get() {
    synchronized (locals) {
      return locals.toArray(new ContextLocal[0]);
    }
  }

  static <T> ContextLocal<T> add(IntFunction<ContextLocal<T>> provider) {
    synchronized (locals) {
      int idx = locals.size();
      ContextLocal<T> local = provider.apply(idx);
      locals.add(local);
      return local;
    }
  }
}
