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
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CountingCompletionHandler<T> {

  private final DefaultContext context;
  private final VertxInternal vertx;
  private int count;
  private int required;
  private Handler<AsyncResult<T>> doneHandler;
  private Throwable cause;
  private boolean failed;

  public CountingCompletionHandler(VertxInternal vertx) {
    this(vertx, 0);
  }

  public CountingCompletionHandler(VertxInternal vertx, int required) {
    this.vertx = vertx;
    this.context = vertx.getOrCreateContext();
    this.required = required;
  }

  public synchronized void complete() {
    count++;
    checkDone();
  }

  public synchronized void failed(Throwable t) {
    if (!failed) {
      // Fail immediately - but only once
      if (doneHandler != null) {
        callHandler(new DefaultFutureResult<T>(t));
      } else {
        cause = t;
      }
      failed = true;
    }
  }

  public synchronized void incRequired() {
    required++;
  }

  public synchronized void setHandler(Handler<AsyncResult<T>> doneHandler) {
    this.doneHandler = doneHandler;
    checkDone();
  }

  private void callHandler(final AsyncResult<T> result) {
    if (vertx.getContext() == context) {
      doneHandler.handle(result);
    } else {
      context.execute(new Runnable() {
        public void run() {
          doneHandler.handle(result);
        }
      });
    }
  }


  void checkDone() {
    if (doneHandler != null) {
      if (cause != null) {
        callHandler(new DefaultFutureResult<T>(cause));
      } else {
        if (count == required) {
          final DefaultFutureResult<T> res = new DefaultFutureResult<T>((T)null);
          callHandler(res);
        }
      }
    }
  }
}
