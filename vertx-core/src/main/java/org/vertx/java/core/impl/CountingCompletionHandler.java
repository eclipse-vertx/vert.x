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
