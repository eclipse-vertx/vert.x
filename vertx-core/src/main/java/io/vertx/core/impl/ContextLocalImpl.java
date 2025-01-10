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

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ContextLocalImpl<T> implements ContextLocal<T> {

  public static <T> ContextLocal<T> create(Class<T> type, Function<T, T> duplicator) {
    synchronized (LocalSeq.class) {
      int idx = LocalSeq.locals.size();
      ContextLocal<T> local = new ContextLocalImpl<>(idx, duplicator);
      LocalSeq.locals.add(local);
      return local;
    }
  }

  final int index;
  final Function<T, T> duplicator;

  public ContextLocalImpl(int index, Function<T, T> duplicator) {
    this.index = index;
    this.duplicator = duplicator;
  }
}
