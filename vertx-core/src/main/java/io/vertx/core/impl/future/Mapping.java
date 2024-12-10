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

import io.vertx.core.Completable;
import io.vertx.core.internal.ContextInternal;

import java.util.function.Function;

/**
 * Function map transformation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Mapping<T, U> extends Operation<U> implements Completable<T> {

  private final Function<? super T, U> successMapper;

  Mapping(ContextInternal context, Function<? super T, U> successMapper) {
    super(context);
    this.successMapper = successMapper;
  }

  @Override
  public void complete(T value, Throwable failure) {
    U result;
    if (failure == null) {
      try {
        result = successMapper.apply(value);
      } catch (Throwable e) {
        result = null;
        failure = e;
      }
    } else {
      result = null;
    }
    handleInternal(result, failure);
  }
}
