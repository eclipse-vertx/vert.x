/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl.pool;

import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.ContextInternal;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class Waiter<C> {

  final ContextImpl context;
  Object metric;

  protected Waiter(ContextImpl context) {
    this.context = context;
  }

  /**
   * Handle connection failure.
   *
   * @param ctx
   * @param failure the failure
   */
  public abstract void handleFailure(ContextInternal ctx, Throwable failure);

  /**
   * Init connection.
   *
   * @param conn the connection
   */
  public abstract void initConnection(C conn);

  /**
   * Handle connection success.
   */
  public abstract void handleConnection(C conn) throws Exception;

  /**
   * @return true if the waiter has been cancelled
   */
  public abstract boolean isCancelled();

}
