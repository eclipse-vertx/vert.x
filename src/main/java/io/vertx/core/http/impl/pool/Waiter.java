/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl.pool;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;

/**
 * This class might seem useless, however we'll add support for pool acquisition timeout
 * so it will be used for keep tracking of the expired waiters.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
final class Waiter<C> {

  public final Handler<AsyncResult<C>> handler;

  Waiter(Handler<AsyncResult<C>> handler) {
    this.handler = handler;
  }
}
