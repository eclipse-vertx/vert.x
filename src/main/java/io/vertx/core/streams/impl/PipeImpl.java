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
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

public class PipeImpl<T> implements Pipe<T> {

  private static final Handler<AsyncResult<Void>> NULL_HANDLER = ar -> {};

  private final Promise<Void> result;
  private final ReadStream<T> src;
  private boolean endOnSuccess = true;
  private boolean endOnFailure = true;
  private WriteStream<T> dst;

  public PipeImpl(ReadStream<T> src) {
    this.src = src;
    this.result = Promise.promise();

    // Set handlers now
    src.endHandler(result::tryComplete);
    src.exceptionHandler(result::tryFail);
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
  public void to(WriteStream<T> ws, Handler<AsyncResult<Void>> completionHandler) {
    if (ws == null) {
      throw new NullPointerException();
    }
    boolean endOnSuccess;
    boolean endOnFailure;
    synchronized (PipeImpl.this) {
      if (dst != null) {
        throw new IllegalStateException();
      }
      dst = ws;
      endOnSuccess = this.endOnSuccess;
      endOnFailure = this.endOnFailure;
    }
    Handler<Void> drainHandler = v -> src.resume();
    src.handler(item -> {
      ws.write(item);
      if (ws.writeQueueFull()) {
        src.pause();
        ws.drainHandler(drainHandler);
      }
    });
    ws.exceptionHandler(err -> result.tryFail(new WriteException(err)));
    src.resume();
    result.future().setHandler(ar -> {
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
      try {
        if (ar.succeeded()) {
          if (endOnSuccess) {
            ws.end(completionHandler);
            return;
          }
        } else {
          Throwable err = ar.cause();
          if (err instanceof WriteException) {
            ar = Future.failedFuture(err.getCause());
            src.resume();
          } else if (endOnFailure){
            ws.end();
          }
        }
      } catch (Exception e) {
        if (endOnFailure) {
          ws.end();
        }
        completionHandler.handle(Future.failedFuture(e));
        return;
      }
      completionHandler.handle(ar);
    });
  }

  public void close() {
    synchronized (this) {
      src.exceptionHandler(null);
      src.handler(null);
      if (dst != null) {
        dst.drainHandler(null);
        dst.exceptionHandler(null);
      }
      if (result.future().isComplete()) {
        return;
      }
    }
    src.resume();
  }

  private static class WriteException extends VertxException {
    private WriteException(Throwable cause) {
      super(cause, true);
    }
  }
}
