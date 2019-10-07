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

package io.vertx.core.net.impl;

import io.netty.util.concurrent.FutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;

import java.util.function.Function;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class FutureListenerAdapter {

  public static <U, T> FutureListener<U> toValue(ContextInternal context, Function<U, T> adapter, Handler<AsyncResult<T>> handler) {
    if (handler != null) {
      return future -> {
        Future<T> res = future.isSuccess() ? Future.succeededFuture(adapter.apply(future.getNow())) : Future.failedFuture(future.cause());
        context.executeFromIO(res, handler);
      };
    } else {
      return null;
    }
  }

  public static <U, T> FutureListener<U> toValue(Function<U, T> adapter, Handler<AsyncResult<T>> handler) {
    if (handler != null) {
      return future -> {
        Future<T> res = future.isSuccess() ? Future.succeededFuture(adapter.apply(future.getNow())) : Future.failedFuture(future.cause());
        handler.handle(res);
      };
    } else {
      return null;
    }
  }

  public static FutureListener<Void> toVoid(ContextInternal context, Handler<AsyncResult<Void>> handler) {
    if (handler != null) {
      return future -> {
        Future<Void> res = future.isSuccess() ? Future.succeededFuture() : Future.failedFuture(future.cause());
        context.executeFromIO(res, handler);
      };
    } else {
      return null;
    }
  }

  public static FutureListener<Void> toVoid(Handler<AsyncResult<Void>> handler) {
    if (handler != null) {
      return future -> {
        Future<Void> res = future.isSuccess() ? Future.succeededFuture() : Future.failedFuture(future.cause());
        handler.handle(res);
      };
    } else {
      return null;
    }
  }
}
