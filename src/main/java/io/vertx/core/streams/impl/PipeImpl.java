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
package io.vertx.core.streams.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

public class PipeImpl<T> implements Pipe<T> {

  private final Promise<Void> result;
  private final Promise<Void> completion;
  private final ReadStream<T> src;
  private boolean endOnSuccess = true;
  private boolean endOnFailure = true;
  private WriteStream<T> dst;

  public PipeImpl(ReadStream<T> src) {
    this.src = src;
    this.result = Promise.promise();
    this.completion = Promise.promise();

    // Set handlers now
    src.endHandler(result::tryComplete);
    src.exceptionHandler(result::tryFail);

    // Handlers
    completion.future().onComplete(this::handleCleanup);
    result.future().onComplete(this::handleClose);
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

  private void handleWriteResult(AsyncResult<Void> ack) {
    if (ack.failed()) {
      result.tryFail(new WriteException(ack.cause()));
    }
  }

  @Override
  public void to(WriteStream<T> dst, Handler<AsyncResult<Void>> completionHandler) {
    to(dst).onComplete(completionHandler);
  }

  private void handleClose(AsyncResult<Void> ar) {
    if (ar.succeeded()) {
      if (dst != null && endOnSuccess) {
        dst.end(completion);
      } else {
        completion.complete();
      }
    } else {
      Throwable err;
      if (ar.cause() instanceof WriteException) {
        src.resume();
        err = ar.cause().getCause();
      } else {
        err = ar.cause();
      }
      if (dst != null && endOnFailure){
        dst.end(ignore -> completion.tryFail(err));
      } else {
        completion.fail(err);
      }
    }
  }

  private void handleCleanup(AsyncResult<Void> ar) {
    try {
      src.handler(null);
    } catch (Exception ignore) {
    }
    try {
      src.exceptionHandler(null);
    } catch (Exception ignore) {
    }
    try {
      src.endHandler(null);
    } catch (Exception ignore) {
    }
    if (dst != null) {
      try {
        dst.drainHandler(null);
      } catch (Exception ignore) {
      }
      try {
        dst.exceptionHandler(null);
      } catch (Exception ignore) {
      }
    }
  }

  @Override
  public Future<Void> to(WriteStream<T> ws) {
    if (ws == null) {
      throw new NullPointerException();
    }
    boolean endOnSuccess;
    boolean endOnFailure;
    boolean closed;
    synchronized (PipeImpl.this) {
      if (dst != null) {
        throw new IllegalStateException();
      }
      dst = ws;
      closed = result.future().isComplete();
      endOnSuccess = this.endOnSuccess;
      endOnFailure = this.endOnFailure;
    }
    if (closed) {
      if (result.future().succeeded()) {
        if (endOnSuccess) {
          dst.end();
        }
      } else {
        if (endOnFailure) {
          dst.end();
        }
      }
    } else {
      Handler<Void> drainHandler = v -> src.resume();
      src.handler(item -> {
        ws.write(item, this::handleWriteResult);
        if (ws.writeQueueFull()) {
          src.pause();
          ws.drainHandler(drainHandler);
        }
      });
      src.resume();
    }
    return completion.future();
  }

  public Future<Void> close() {
    if (result.tryFail(new VertxException("Pipe closed prematurely", true))) {
      src.resume();
    }
    return completion.future();
  }

  @Override
  public void close(Handler<AsyncResult<Void>> handler) {
    close().onComplete(handler);
  }

  private static class WriteException extends VertxException {
    private WriteException(Throwable cause) {
      super(cause, true);
    }
  }
}
