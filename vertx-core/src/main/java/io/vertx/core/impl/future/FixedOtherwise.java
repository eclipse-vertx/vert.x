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
 * Otherwise value transformation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class FixedOtherwise<T> extends Operation<T> implements Completable<T> {

  private final T fallback;

  FixedOtherwise(ContextInternal context, T fallback) {
    super(context);
    this.fallback = fallback;
  }

  @Override
  public void complete(T result, Throwable failure) {
    handleInternal(failure != null ? fallback : result, null);
  }
}
