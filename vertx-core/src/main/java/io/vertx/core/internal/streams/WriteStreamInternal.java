/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.streams;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Completable;
import io.vertx.core.Handler;
import io.vertx.core.impl.future.FutureBase;
import io.vertx.core.impl.future.FutureImpl;
import io.vertx.core.streams.WriteStream;

/**
 * Internal extension of WriteStream returning a {@link WriteResult}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface WriteStreamInternal<T> extends WriteStream<T> {

  /**
   * Like {@link WriteStream#write}, returning the {@link WriteResult} of the submission.
   */
  @Override
  WriteResult write(T data);

  @Override
  WriteResult end();

  /**
   * Like {@link WriteStream#end(Object)}: write {@code data} then end the stream after the write succeeded,
   * the returned future completes with the end. The result is never unwritable and never signals: it is the
   * last write of the stream, there is no verdict a caller could act on.
   */
  @Override
  default WriteResult end(T data) {

    FutureBase<Void> write = (FutureBase<Void>) write(data);

    // Compose adapted to our case
    class WritableComposition extends FutureImpl<Void> implements Completable<Void>, WriteResult {
      public WritableComposition() {
      }

      @Override
      public void complete(Void result, Throwable failure) {
        if (failure != null) {
          fail(failure);
        } else {
          WriteResult end = end();
          FutureImpl<Void> bluh = (FutureImpl<Void>) end;
          bluh.addListener(this::completeInternal);
        }
      }

      @Override
      public boolean isWritable() {
        return true;
      }

      @Override
      public WriteResult drainHandler(Handler<Void> handler) {
        return this;
      }

      @Override
      public WriteResult disposeHandler(Handler<Void> handler) {
        return this;
      }
    }

    WritableComposition result = new WritableComposition();
    write.addListener(result);
    return result;
  }

  @Override
  WriteStreamInternal<T> setWriteQueueMaxSize(int maxSize);

  @Override
  WriteStreamInternal<T> drainHandler(@Nullable Handler<Void> handler);

  @Override
  WriteStreamInternal<T> exceptionHandler(@Nullable Handler<Throwable> handler);

}
