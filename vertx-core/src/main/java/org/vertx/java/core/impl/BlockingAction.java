/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.impl;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;

/**
 * <p>Internal class used to run specific blocking actions on the worker pool.</p>
 *
 * <p>This class shouldn't be used directlty from user applications.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BlockingAction<T>  {

  protected DefaultContext context;

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

    Runnable runner = new Runnable() {
      public void run() {
        final DefaultFutureResult<T> res = new DefaultFutureResult<>();
        try {
          res.setResult(action());
        } catch (Exception e) {
          res.setFailure(e);
        }
        if (handler != null) {
          context.execute(new Runnable() {
            public void run() {
              res.setHandler(handler);
            }
          });
        }
      }
    };

    context.executeOnOrderedWorkerExec(runner);
  }

  public abstract T action();

}
