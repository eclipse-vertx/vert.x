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
package io.vertx.core.impl.future;

import io.vertx.core.impl.ContextInternal;

import java.util.function.Function;

class Otherwise<T> extends Operation<T> implements Listener<T> {

  private final Function<Throwable, T> mapper;

  Otherwise(ContextInternal context, Function<Throwable, T> mapper) {
    super(context);
    this.mapper = mapper;
  }

  @Override
  public void onSuccess(T value) {
    tryComplete(value);
  }

  @Override
  public void onFailure(Throwable failure) {
    T result;
    try {
      result = mapper.apply(failure);
    } catch (Throwable e) {
      tryFail(e);
      return;
    }
    tryComplete(result);
  }
}
