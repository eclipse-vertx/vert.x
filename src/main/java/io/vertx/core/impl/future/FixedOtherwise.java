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

/**
 * Otherwise value transformation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class FixedOtherwise<T> extends Operation<T> implements Listener<T> {

  private final T value;

  FixedOtherwise(ContextInternal context, T value) {
    super(context);
    this.value = value;
  }

  @Override
  public void onSuccess(T value) {
    tryComplete(value);
  }

  @Override
  public void onFailure(Throwable failure) {
    tryComplete(value);
  }
}
