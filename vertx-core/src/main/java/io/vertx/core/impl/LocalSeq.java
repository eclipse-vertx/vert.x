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

import io.vertx.core.internal.ContextInternal;
import io.vertx.core.spi.context.storage.ContextLocal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class LocalSeq {

  // 0 : reserved slot for local context map
  static final List<ContextLocal<?>> locals = new ArrayList<>();

  static {
    reset();
  }

  /**
   * Hook for testing purposes
   */
  public synchronized static void reset() {
    // 0 : reserved slot for local context map
    locals.clear();
    locals.add(ContextInternal.LOCAL_MAP);
  }

  synchronized static ContextLocal<?>[] get() {
    return locals.toArray(new ContextLocal[0]);
  }
}
