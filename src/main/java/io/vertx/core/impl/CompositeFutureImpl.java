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

package io.vertx.core.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Handler;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CompositeFutureImpl extends FutureImpl<CompositeFuture> implements CompositeFuture {

  public static CompositeFuture all(Future<?>... results) {
    CompositeFutureImpl composite = new CompositeFutureImpl(results);
    for (int i = 0;i < results.length;i++) {
      int index = i;
      results[i].setHandler(ar -> {
        if (ar.succeeded()) {
          composite.flag |= 1 << index;
          if (!composite.isComplete() && composite.flag == (1 << results.length) - 1) {
            composite.complete(composite);
          }
        } else {
          if (!composite.isComplete()) {
            composite.fail(ar.cause());
          }
        }
      });
    }
    return composite;
  }

  public static CompositeFuture any(Future<?>... results) {
    CompositeFutureImpl composite = new CompositeFutureImpl(results);
    for (int i = 0;i < results.length;i++) {
      int index = i;
      results[i].setHandler(ar -> {
        if (ar.succeeded()) {
          if (!composite.isComplete()) {
            composite.complete(composite);
          }
        } else {
          composite.flag |= 1 << index;
          if (!composite.isComplete() && composite.flag == (1 << results.length) - 1) {
            composite.fail(ar.cause());
          }
        }
      });
    }
    return composite;
  }

  private final Future[] results;
  private int flag;

  private CompositeFutureImpl(Future<?>... results) {
    this.results = results;
  }

  @Override
  public CompositeFuture setHandler(Handler<AsyncResult<CompositeFuture>> handler) {
    return (CompositeFuture) super.setHandler(handler);
  }

  @Override
  public Throwable cause(int index) {
    return results[index].cause();
  }

  @Override
  public boolean succeeded(int index) {
    return results[index].succeeded();
  }

  @Override
  public boolean failed(int index) {
    return results[index].failed();
  }

  @Override
  public boolean isComplete(int index) {
    return results[index].isComplete();
  }

  @Override
  public <T> T result(int index) {
    if (index < 0 || index > results.length) {
      throw new IndexOutOfBoundsException();
    }
    return (T) results[index].result();
  }

  @Override
  public int size() {
    return results.length;
  }
}
