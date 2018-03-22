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

import io.vertx.core.impl.ContextInternal;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class Waiter<C> {

  public final ContextInternal context;

  protected Waiter(ContextInternal context) {
    this.context = context;
  }

  /**
   * Handle connection failure, this callback is on an event loop thread.
   *
   * @param ctx the context used to create the connection
   * @param failure the failure
   */
  public abstract void handleFailure(ContextInternal ctx, Throwable failure);

  /**
   * Init connection, this callback is on an event loop thread.
   *
   * @param ctx the context used to create the connection
   * @param conn the connection
   */
  public abstract void initConnection(ContextInternal ctx, C conn);

  /**
   * Handle connection success, this callback is on an event loop thread.
   *
   * @param ctx the context used to create the connection
   * @param conn the connection
   * @return whether the waiter uses the connection
   */
  public abstract boolean handleConnection(ContextInternal ctx, C conn) throws Exception;

}
