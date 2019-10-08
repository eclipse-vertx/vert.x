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

package io.vertx.core.impl;

import io.vertx.core.Future;
import io.vertx.core.spi.FutureFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FutureFactoryImpl implements FutureFactory {

  private static final SucceededFuture EMPTY = new SucceededFuture<>(null, null);

  @Override
  public <T> PromiseInternal<T> promise() {
    return new FutureImpl<>();
  }

  @Override
  public <T> Future<T> succeededFuture() {
    @SuppressWarnings("unchecked")
    Future<T> fut = EMPTY;
    return fut;
  }

  @Override
  public <T> Future<T> succeededFuture(T result) {
    return new SucceededFuture<>(null, result);
  }

  @Override
  public <T> Future<T> failedFuture(Throwable t) {
    return new FailedFuture<>(null, t);
  }

  @Override
  public <T> Future<T> failureFuture(String failureMessage) {
    return new FailedFuture<>(null, failureMessage);
  }

  @Override
  public <T> PromiseInternal<T> promise(ContextInternal context) {
    return new FutureImpl<>(context);
  }

  @Override
  public <T> Future<T> succeededFuture(ContextInternal context) {
    return new SucceededFuture<>(context, null);
  }

  @Override
  public <T> Future<T> succeededFuture(ContextInternal context, T result) {
    return new SucceededFuture<>(context, result);
  }

  @Override
  public <T> Future<T> failedFuture(ContextInternal context, Throwable t) {
    return new FailedFuture<>(context, t);
  }

  @Override
  public <T> Future<T> failedFuture(ContextInternal context, String failureMessage) {
    return new FailedFuture<>(context, failureMessage);
  }
}
