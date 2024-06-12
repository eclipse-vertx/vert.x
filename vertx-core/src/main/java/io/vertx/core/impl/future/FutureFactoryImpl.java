/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl.future;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.spi.FutureFactory;

import java.util.concurrent.CompletionStage;

public class FutureFactoryImpl implements FutureFactory {

  @Override
  public <T> Promise<T> promise() {
    return new PromiseImpl<>();
  }

  @Override
  public <T> Future<T> succeededFuture(T result) {
    return result == null ? SucceededFuture.EMPTY : new SucceededFuture<>(result);
  }

  @Override
  public <T> Future<T> failedFuture(Throwable t) {
    return new FailedFuture<>(t);
  }

  @Override
  public <T> Future<T> failedFuture(String failureMessage) {
    return new FailedFuture<>(failureMessage);
  }

  @Override
  public CompositeFuture all(Future<?>... results) {
    return CompositeFutureImpl.all(results);
  }

  @Override
  public CompositeFuture any(Future<?>... results) {
    return CompositeFutureImpl.any(results);
  }

  @Override
  public CompositeFuture join(Future<?>... results) {
    return CompositeFutureImpl.join(results);
  }

  @Override
  public <T> Future<T> fromCompletionStage(CompletionStage<T> completionStage, Context context) {
    Promise<T> promise = ((io.vertx.core.impl.ContextInternal) context).promise();
    completionStage.whenComplete((value, err) -> {
      if (err != null) {
        promise.fail(err);
      } else {
        promise.complete(value);
      }
    });
    return promise.future();
  }
}
