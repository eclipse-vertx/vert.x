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

import io.vertx.core.ThreadingModel;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.spi.context.storage.AccessMode;
import io.vertx.core.spi.context.storage.ContextLocal;

import java.util.function.Supplier;

/**
 * Base class for context.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class ContextBase implements ContextInternal {

  final Object[] locals;

  ContextBase(Object[] locals) {
    this.locals = locals;
  }

  public ContextInternal beginDispatch() {
    VertxImpl vertx = (VertxImpl) owner();
    return vertx.beginDispatch(this);
  }

  public void endDispatch(ContextInternal previous) {
    VertxImpl vertx = (VertxImpl) owner();
    vertx.endDispatch(previous);
  }

  public final <T> T getLocal(ContextLocal<T> key, AccessMode accessMode) {
    ContextLocalImpl<T> internalKey = (ContextLocalImpl<T>) key;
    int index = internalKey.index;
    if (index >= locals.length) {
      throw new IllegalArgumentException();
    }
    Object res = accessMode.get(locals, index);
    return (T) res;
  }

  public final <T> T getLocal(ContextLocal<T> key, AccessMode accessMode, Supplier<? extends T> initialValueSupplier) {
    ContextLocalImpl<T> internalKey = (ContextLocalImpl<T>) key;
    int index = internalKey.index;
    if (index >= locals.length) {
      throw new IllegalArgumentException("Invalid key index: " + index);
    }
    Object res = accessMode.getOrCreate(locals, index, (Supplier<Object>) initialValueSupplier);
    return (T) res;
  }

  public final <T> void putLocal(ContextLocal<T> key, AccessMode accessMode, T value) {
    ContextLocalImpl<T> internalKey = (ContextLocalImpl<T>) key;
    int index = internalKey.index;
    if (index >= locals.length) {
      throw new IllegalArgumentException();
    }
    accessMode.put(locals, index, value);
  }
}
