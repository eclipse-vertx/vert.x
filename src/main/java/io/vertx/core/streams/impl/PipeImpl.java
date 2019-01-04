/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.streams.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

public class PipeImpl<T> implements Pipe<T> {

  private static final Handler<AsyncResult<Void>> NULL_HANDLER = ar -> {};

  private final Future<Void> result;
  private final ReadStream<T> src;
  private boolean endOnSuccess = true;
  private boolean endOnFailure = true;

  public PipeImpl(ReadStream<T> src) {
    this.src = src;
    this.result = Future.<Void>future();

    // Set handler now
    src.endHandler(result::tryComplete);
  }

  @Override
  public synchronized Pipe<T> endOnFailure(boolean end) {
    endOnFailure = end;
    return this;
  }

  @Override
  public synchronized Pipe<T> endOnSuccess(boolean end) {
    endOnSuccess = end;
    return this;
  }

  @Override
  public synchronized Pipe<T> endOnComplete(boolean end) {
    endOnSuccess = end;
    endOnFailure = end;
    return this;
  }

  @Override
  public void to(WriteStream<T> ws) {
    to(ws, NULL_HANDLER);
  }

  @Override
  public void to(WriteStream<T> dst, Handler<AsyncResult<Void>> completionHandler) {
    if (dst == null) {
      throw new NullPointerException();
    }
    boolean endOnSuccess;
    boolean endOnFailure;
    synchronized (PipeImpl.this) {
      endOnSuccess = this.endOnSuccess;
      endOnFailure = this.endOnFailure;
    }
    dst.drainHandler(v -> {
      src.resume();
    });
    src.handler(item -> {
      dst.write(item);
      if (dst.writeQueueFull()) {
        src.pause();
      }
    });
    dst.exceptionHandler(err -> result.tryFail(new WriteException(err)));
    src.exceptionHandler(result::tryFail);
    src.resume();
    result.setHandler(ar -> {
      try {
        src.exceptionHandler(null);
        src.handler(null);
        dst.drainHandler(null);
        dst.exceptionHandler(null);
        if (ar.succeeded()) {
          if (endOnSuccess) {
            dst.end();
          }
        } else {
          Throwable err = ar.cause();
          if (err instanceof WriteException) {
            ar = Future.failedFuture(err.getCause());
          } else if (endOnFailure){
            dst.end();
          }
        }
      } catch (Exception e) {
        if (endOnFailure) {
          dst.end();
        }
        completionHandler.handle(Future.failedFuture(e));
        return;
      }
      completionHandler.handle(ar);
    });
  }

  private static class WriteException extends VertxException {
    private WriteException(Throwable cause) {
      super(cause, true);
    }
  }
}
