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

package io.vertx.core;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.impl.future.CompositeFutureImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * The composite future wraps a list of {@link Future futures}, it is useful when several futures
 * needs to be coordinated.
 * The handlers set for the coordinated futures are overridden by the handler of the composite future.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface CompositeFuture extends Future<CompositeFuture> {

  @Override
  CompositeFuture onComplete(Handler<AsyncResult<CompositeFuture>> handler);

  @Override
  default CompositeFuture onSuccess(Handler<? super CompositeFuture> handler) {
    Future.super.onSuccess(handler);
    return this;
  }

  @Override
  default CompositeFuture onFailure(Handler<? super Throwable> handler) {
    Future.super.onFailure(handler);
    return this;
  }

  /**
   * Returns a cause of a wrapped future
   *
   * @param index the wrapped future index
   */
  Throwable cause(int index);

  /**
   * Returns true if a wrapped future is succeeded
   *
   * @param index the wrapped future index
   */
  boolean succeeded(int index);

  /**
   * Returns true if a wrapped future is failed
   *
   * @param index the wrapped future index
   */
  boolean failed(int index);

  /**
   * Returns true if a wrapped future is completed
   *
   * @param index the wrapped future index
   */
  boolean isComplete(int index);

  /**
   * Returns the result of a wrapped future
   *
   * @param index the wrapped future index
   */
  <T> T resultAt(int index);

  /**
   * @return the number of wrapped future
   */
  int size();

  /**
   * @return a list of the current completed values. If one future is not yet resolved or is failed, {@code} null
   *         will be used
   */
  @GenIgnore
  default <T> List<T> list() {
    int size = size();
    ArrayList<T> list = new ArrayList<>(size);
    for (int index = 0;index < size;index++) {
      list.add(resultAt(index));
    }
    return list;
  }

  /**
   * @return a list of all the eventual failure causes. If no future failed, returns a list of null values.
   */
  @GenIgnore
  default List<Throwable> causes() {
    int size = size();
    ArrayList<Throwable> list = new ArrayList<>(size);
    for (int index = 0; index < size; index++) {
      list.add(cause(index));
    }
    return list;
  }
}
