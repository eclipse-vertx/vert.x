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

package io.vertx.core.streams;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.streams.impl.PipeImpl;

/**
 * Represents a stream of items that can be read from.
 * <p>
 * Any class that implements this interface can be used by a {@link Pipe} to pipe data from it
 * to a {@link WriteStream}.
 * <p>
 * <h3>Streaming mode</h3>
 * The stream is either in <i>flowing</i> or <i>fetch</i> mode.
 * <ul>
 *   <i>Initially the stream is in <i>flowing</i> mode.</i>
 *   <li>When the stream is in <i>flowing</i> mode, elements are delivered to the {@code handler}.</li>
 *   <li>When the stream is in <i>fetch</i> mode, only the number of requested elements will be delivered to the {@code handler}.</li>
 * </ul>
 * The mode can be changed with the {@link #pause()}, {@link #resume()} and {@link #fetch} methods:
 * <ul>
 *   <li>Calling {@link #resume()} sets the <i>flowing</i> mode</li>
 *   <li>Calling {@link #pause()} sets the <i>fetch</i> mode and resets the demand to {@code 0}</li>
 *   <li>Calling {@link #fetch(long)} requests a specific amount of elements and adds it to the actual demand</li>
 * </ul>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen(concrete = false)
public interface ReadStream<T> extends StreamBase {

  /**
   * Set an exception handler on the read stream.
   *
   * @param handler  the exception handler
   * @return a reference to this, so the API can be used fluently
   */
  ReadStream<T> exceptionHandler(Handler<Throwable> handler);

  /**
   * Set a data handler. As data is read, the handler will be called with the data.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ReadStream<T> handler(@Nullable Handler<T> handler);

  /**
   * Pause the {@code ReadStream}, it sets the buffer in {@code fetch} mode and clears the actual demand.
   * <p>
   * While it's paused, no data will be sent to the data {@code handler}.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ReadStream<T> pause();

  /**
   * Resume reading, and sets the buffer in {@code flowing} mode.
   * <p/>
   * If the {@code ReadStream} has been paused, reading will recommence on it.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ReadStream<T> resume();

  /**
   * Fetch the specified {@code amount} of elements. If the {@code ReadStream} has been paused, reading will
   * recommence with the specified {@code amount} of items, otherwise the specified {@code amount} will
   * be added to the current stream demand.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ReadStream<T> fetch(long amount);

  /**
   * Set an end handler. Once the stream has ended, and there is no more data to be read, this handler will be called.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ReadStream<T> endHandler(@Nullable Handler<Void> endHandler);

  /**
   * Pause this stream and return a {@link Pipe} to transfer the elements of this stream to a destination {@link WriteStream}.
   * <p/>
   * The stream will be resumed when the pipe will be wired to a {@code WriteStream}.
   *
   * @return a pipe
   */
  default Pipe<T> pipe() {
    pause();
    return new PipeImpl<>(this);
  }

  /**
   * Same as {@link #pipeTo(WriteStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<Void> pipeTo(WriteStream<T> dst) {
    Promise<Void> promise = Promise.promise();
    new PipeImpl<>(this).to(dst, promise);
    return promise.future();
  }

  /**
   * Pipe this {@code ReadStream} to the {@code WriteStream}.
   * <p>
   * Elements emitted by this stream will be written to the write stream until this stream ends or fails.
   * <p>
   * Once this stream has ended or failed, the write stream will be ended and the {@code handler} will be
   * called with the result.
   *
   * @param dst the destination write stream
   */
  default void pipeTo(WriteStream<T> dst, Handler<AsyncResult<Void>> handler) {
    new PipeImpl<>(this).to(dst, handler);
  }
}
