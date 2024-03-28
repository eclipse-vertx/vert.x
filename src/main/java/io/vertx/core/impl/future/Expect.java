/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl.future;

import io.vertx.core.Expectation;
import io.vertx.core.VertxException;
import io.vertx.core.impl.ContextInternal;

/**
 * Implements expectation.
 */
public class Expect<T> extends Operation<T> implements Listener<T> {

  private final Expectation<? super T> expectation;

  public Expect(ContextInternal context, Expectation<? super T> expectation) {
    super(context);
    this.expectation = expectation;
  }

  @Override
  public void onSuccess(T value) {
    Throwable err = null;
    try {
      if (!expectation.test(value)) {
        err = expectation.describe(value);
        if (err == null) {
          err = new VertxException("Unexpected result: " + value, true);
        }
      }
    } catch (Throwable e) {
      err = e;
    }
    if (err != null) {
      tryFail(err);
    } else {
      tryComplete(value);
    }
  }

  @Override
  public void onFailure(Throwable failure) {
    tryFail(failure);
  }
}
