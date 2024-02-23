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

import io.vertx.core.spi.context.locals.AccessMode;
import io.vertx.core.spi.context.locals.ContextKey;

import java.util.function.Supplier;

/**
 * Base class for context.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class ContextBase {

  final Object[] locals;

  ContextBase(Object[] locals) {
    this.locals = locals;
  }

  public final <T> T getLocal(ContextKey<T> key, AccessMode accessMode) {
    ContextKeyImpl<T> internalKey = (ContextKeyImpl<T>) key;
    int index = internalKey.index;
    if (index >= locals.length) {
      throw new IllegalArgumentException();
    }
    Object res = accessMode.get(locals, index);
    return (T) res;
  }

  public final <T> T getLocal(ContextKey<T> key, AccessMode accessMode, Supplier<? extends T> initialValueSupplier) {
    ContextKeyImpl<T> internalKey = (ContextKeyImpl<T>) key;
    int index = internalKey.index;
    if (index >= locals.length) {
      throw new IllegalArgumentException("Invalid key index: " + index);
    }
    Object res = accessMode.getOrCreate(locals, index, (Supplier<Object>) initialValueSupplier);
    return (T) res;
  }

  public final <T> void putLocal(ContextKey<T> key, AccessMode accessMode, T value) {
    ContextKeyImpl<T> internalKey = (ContextKeyImpl<T>) key;
    int index = internalKey.index;
    if (index >= locals.length) {
      throw new IllegalArgumentException();
    }
    accessMode.put(locals, index, value);
  }
}
