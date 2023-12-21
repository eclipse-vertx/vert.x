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

import io.vertx.core.spi.context.ContextKey;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Base class for context.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class ContextBase {

  private static final VarHandle LOCALS_UPDATER = MethodHandles.arrayElementVarHandle(Object[].class);

  final Object[] locals;

  ContextBase(Object[] locals) {
    this.locals = locals;
  }

  public final <T> T getLocal(ContextKey<T> key) {
    ContextKeyImpl<T> internalKey = (ContextKeyImpl<T>) key;
    int index = internalKey.index;
    if (index >= locals.length) {
      throw new IllegalArgumentException();
    }
    Object res = LOCALS_UPDATER.getVolatile(locals, index);
    return (T) res;
  }

  public final <T> void putLocal(ContextKey<T> key, T value) {
    ContextKeyImpl<T> internalKey = (ContextKeyImpl<T>) key;
    int index = internalKey.index;
    if (index >= locals.length) {
      throw new IllegalArgumentException();
    }
    LOCALS_UPDATER.setVolatile(locals, index, value);
  }
}
