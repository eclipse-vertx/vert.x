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

import io.vertx.core.Completable;
import io.vertx.core.Expectation;
import io.vertx.core.VertxException;
import io.vertx.core.internal.ContextInternal;

/**
 * Implements expectation.
 */
public class Expect<T> extends Operation<T> implements Completable<T> {

  private final Expectation<? super T> expectation;

  public Expect(ContextInternal context, Expectation<? super T> expectation) {
    super(context);
    this.expectation = expectation;
  }

  @Override
  public void complete(T result, Throwable failure) {
    if (failure == null) {
      try {
        if (!expectation.test(result)) {
          failure = expectation.describe(result);
          if (failure == null) {
            failure = new VertxException("Unexpected result: " + result, true);
          }
        }
      } catch (Throwable e) {
        failure = e;
      }
    }
    handleInternal(result, failure);
  }
}
