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

package io.vertx.core;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.impl.CompositeFutureImpl;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface CompositeFuture extends Future<CompositeFuture> {

  static <T1, T2> CompositeFuture and(Future<T1> f1, Future<T2> f2) {
    return new CompositeFutureImpl(f1, f2);
  }

  @Override
  CompositeFuture setHandler(Handler<AsyncResult<CompositeFuture>> handler);

  Throwable cause(int index);

  boolean succeeded(int index);

  boolean failed(int index);

  boolean isComplete(int index);

  <T> T result(int index);

  int size();

}
