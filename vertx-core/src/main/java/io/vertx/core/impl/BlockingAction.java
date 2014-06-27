/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * <p>Internal class used to run specific blocking actions on the worker pool.</p>
 *
 * <p>This class shouldn't be used directly from user applications.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BlockingAction<T>  {

  protected ContextImpl context;

  private final VertxInternal vertx;
  private final Handler<AsyncResult<T>> handler;

  public BlockingAction(VertxInternal vertx, Handler<AsyncResult<T>> handler) {
    this.vertx = vertx;
    this.handler = handler;
  }

  /**
   * Run the blocking action using a thread from the worker pool.
   */
  public void run() {
    context = vertx.getOrCreateContext();
    context.executeOnOrderedWorkerExec(() -> {
      final FutureResultImpl<T> res = new FutureResultImpl<>();
      try {
        res.setResult(action());
      } catch (Exception e) {
        res.setFailure(e);
      }
      if (handler != null) {
        context.execute(() -> res.setHandler(handler), false);
      }
    });
  }

  public abstract T action();

}
