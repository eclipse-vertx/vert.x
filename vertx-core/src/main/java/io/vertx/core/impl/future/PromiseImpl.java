/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl.future;

import io.vertx.core.Future;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;

/**
 * Promise implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public final class PromiseImpl<T> extends FutureImpl<T> implements PromiseInternal<T> {

  /**
   * Create a promise that hasn't completed yet
   */
  public PromiseImpl() {
    super();
  }

  /**
   * Create a promise that hasn't completed yet
   */
  public PromiseImpl(ContextInternal context) {
    super(context);
  }

  @Override
  public Future<T> future() {
    return this;
  }

  @Override
  public void operationComplete(io.netty.util.concurrent.Future<T> future) {
    if (future.isSuccess()) {
      complete(future.getNow());
    } else {
      fail(future.cause());
    }
  }
}
