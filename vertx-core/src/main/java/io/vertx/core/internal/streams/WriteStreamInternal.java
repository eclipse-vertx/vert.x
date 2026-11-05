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
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
  Future<Void> end();

  /**
   * Like {@link WriteStream#end(Object)}: write {@code data} then end the stream after the write succeeded,
   * the returned future completes with the end. The result is never unwritable and never signals: it is the
   * last write of the stream, there is no verdict a caller could act on.
   */
  @Override
  default WriteResult end(T data) {
    WritableWrite ended = new WritableWrite();
    write(data).compose(v -> end()).onComplete(ended);
    return ended;
  }

  @Override
  WriteStreamInternal<T> setWriteQueueMaxSize(int maxSize);

  @Override
  WriteStreamInternal<T> drainHandler(@Nullable Handler<Void> handler);

  @Override
  WriteStreamInternal<T> exceptionHandler(@Nullable Handler<Throwable> handler);

}
