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
import java.util.function.UnaryOperator;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ContextLocalImpl<T> implements ContextLocal<T> {

  public static <T> ContextLocal<T> registerLocal(Class<T> type) {
    return registerLocal(type, (UnaryOperator<T>) ContextLocalImpl.IDENTITY);
  }

  public static <T> ContextLocal<T> registerLocal(Class<T> type, Function<T, ? extends T> duplicator) {
    return LocalSeq.add(idx -> new ContextLocalImpl<>(idx, type, duplicator));
  }

  public static final UnaryOperator<?> IDENTITY = UnaryOperator.identity();

  final int index;
  final Class<T> type;
  final  Function<T, ? extends T> duplicator;

  ContextLocalImpl(int index, Class<T> type, Function<T, ? extends T> duplicator) {
    this.index = index;
    this.type = type;
    this.duplicator = duplicator;
  }
}
