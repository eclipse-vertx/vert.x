/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.logic;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;

/**
 * Interface containing method definitions for converting synchronous blocking calls
 * to asynchronous operations.
 *
 * @author Sinri Edogawa
 */
interface AsyncLogicBlock extends AsyncLogicCore {
  private boolean isInNonBlockContext() {
    Context currentContext = Vertx.currentContext();
    return currentContext != null && currentContext.isEventLoopContext();
  }

  /**
   * Converts a {@link CompletableFuture} to a Vert.x {@link Future}.
   * <p>
   * The conversion ensures proper context handling, executing completion callbacks
   * on the appropriate event loop thread when available.
   *
   * @param <R>               the type of the asynchronous return value
   * @param completableFuture the given {@link CompletableFuture}
   * @return the converted {@link Future}
   */
  default <R> Future<R> asyncTransformCompletableFuture(CompletableFuture<R> completableFuture) {
    Promise<R> promise = Promise.promise();
    Context currentContext = Vertx.currentContext();

      completableFuture.whenComplete((r, t) -> {
      Runnable completeAction = () -> {
        try {
          if (t != null) {
            promise.fail(t);
          } else {
            promise.complete(r);
          }
        } catch (Exception e) {
          promise.tryFail(e);
        }
      };

      // If there is no context or already on the correct event loop thread, execute directly
      if (currentContext == null) {
        completeAction.run();
      } else if (currentContext.isEventLoopContext()) {
        currentContext.runOnContext(v -> completeAction.run());
      } else {
        // In a worker thread, execute directly
        completeAction.run();
      }
    });

    return promise.future();
  }

  /**
   * Converts a {@link java.util.concurrent.Future} to a Vert.x {@link Future}.
   * <p>
   * If called from a non-blocking context (event loop), the conversion uses
   * {@code executeBlocking} to avoid blocking the event loop. Otherwise, it
   * blocks the current thread to get the result.
   *
   * @param <R>       the type of the asynchronous return value
   * @param rawFuture the given {@link java.util.concurrent.Future}
   * @return the converted {@link Future}
   */
  default <R> Future<R> asyncTransformRawFuture(java.util.concurrent.Future<R> rawFuture) {
    if (isInNonBlockContext()) {
      return vertx().executeBlocking(rawFuture::get);
    } else {
      try {
        var r = rawFuture.get();
        return Future.succeededFuture(r);
      } catch (InterruptedException | ExecutionException e) {
        return Future.failedFuture(e);
      }
    }
  }

  /**
   * Converts a {@link java.util.concurrent.Future} to a Vert.x {@link Future}
   * using polling with a specified sleep interval.
   * <p>
   * This method repeatedly checks if the future is done, sleeping for the specified
   * duration between checks. Once the future is done, it retrieves the result.
   *
   * @param <R>       the type of the asynchronous return value
   * @param rawFuture the given {@link java.util.concurrent.Future}
   * @param sleepTime the wait time between checks in milliseconds
   * @return the converted {@link Future}
   */
  default <R> Future<R> asyncTransformRawFuture(java.util.concurrent.Future<R> rawFuture, long sleepTime) {
    return asyncCallRepeatedly(repeatedlyCallTask -> {
      if (rawFuture.isDone()) {
        repeatedlyCallTask.stop();
        return Future.succeededFuture();
      }
      return this.asyncSleep(sleepTime);
    })
      .compose(over -> {
        if (rawFuture.isCancelled()) {
          return Future
            .failedFuture(new java.util.concurrent.CancellationException("Raw Future Cancelled"));
        }
        try {
          var r = rawFuture.get();
          return Future.succeededFuture(r);
        } catch (InterruptedException | ExecutionException e) {
          return Future.failedFuture(e);
        }
      });
  }

  /**
   * Blocks and waits for an asynchronous task to complete, then returns its result.
   * <p>
   * This method uses {@link CountDownLatch} to implement blocking wait, avoiding CPU
   * spinning. The method blocks the current thread until the asynchronous task completes
   * (successfully or with failure).
   * <p>
   * <strong>Important restrictions:</strong>
   * <ul>
   *   <li>This method <strong>must not</strong> be called from an EventLoop thread,
   *       otherwise it will throw {@link IllegalThreadStateException}</li>
   *   <li>This method can only be called from Worker threads, virtual threads, or
   *       regular threads</li>
   *   <li>It is recommended to use this method in Verticles with
   *       {@link ThreadingModel#WORKER} or {@link ThreadingModel#VIRTUAL_THREAD}</li>
   * </ul>
   * <p>
   * <strong>Exception handling:</strong>
   * <ul>
   *   <li>If the asynchronous task fails, this method throws {@link RuntimeException},
   *       with the original exception wrapped or thrown directly (if it is already a
   *       RuntimeException)</li>
   *   <li>If the current thread is interrupted during waiting, it throws
   *       {@link RuntimeException} and restores the thread's interrupt status</li>
   *   <li>If called from an EventLoop thread, it immediately throws
   *       {@link IllegalThreadStateException}</li>
   * </ul>
   * <p>
   * <strong>Return value:</strong>
   * <ul>
   *   <li>If the asynchronous task completes successfully, returns the task's result
   *       (which may be {@code null})</li>
   *   <li>If the asynchronous task fails, throws an exception instead of returning
   *       {@code null}</li>
   * </ul>
   * <p>
   * Use this method only when necessary, and ensure it fits your use case before using it.
   *
   * @param <T>                         the type of the value returned by the asynchronous task
   * @param longTermAsyncProcessFuture  a {@link Future} returned by a long-running
   *                                    asynchronous task, must not be {@code null}
   * @return the value returned by the asynchronous task; if the task completes successfully
   *         but the result is {@code null}, returns {@code null}
   * @throws IllegalThreadStateException if this method is called from an EventLoop thread
   * @throws RuntimeException            if the asynchronous task fails, or the current
   *                                     thread is interrupted during waiting
   */
  @Nullable
  default <T> T blockAwait(Future<T> longTermAsyncProcessFuture) {
    if (isInNonBlockContext()) {
      throw new IllegalThreadStateException("Cannot call blockAwait in event loop context");
    }

    // 使用 CountDownLatch 替代忙等待，避免 CPU 空转
    CountDownLatch latch = new CountDownLatch(1);
    longTermAsyncProcessFuture.onComplete(ar -> latch.countDown());

    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while waiting for async task", e);
    }

    // 检查任务是否失败，如果有异常则抛出，而不是返回 null
    if (longTermAsyncProcessFuture.failed()) {
      Throwable cause = longTermAsyncProcessFuture.cause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      throw new RuntimeException(cause);
    }

    return longTermAsyncProcessFuture.result();
  }
}
