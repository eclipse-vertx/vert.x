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

/**
 * Map value transformation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class FixedMapping<T, U> extends Operation<U> implements Completable<T> {

  private final U value;

  FixedMapping(ContextInternal context, U value) {
    super(context);
    this.value = value;
  }

  @Override
  public void complete(T result, Throwable failure) {
    handleInternal(this.value, failure);
  }
}
