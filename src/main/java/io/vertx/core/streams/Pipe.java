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
package io.vertx.core.streams;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * Pipe data from a {@link ReadStream} to a {@link WriteStream} and performs flow control where necessary to
 * prevent the write stream buffer from getting overfull.
 * <p>
 * Instances of this class read items from a {@link ReadStream} and write them to a {@link WriteStream}. If data
 * can be read faster than it can be written this could result in the write queue of the {@link WriteStream} growing
 * without bound, eventually causing it to exhaust all available RAM.
 * <p>
 * To prevent this, after each write, instances of this class check whether the write queue of the {@link
 * WriteStream} is full, and if so, the {@link ReadStream} is paused, and a {@code drainHandler} is set on the
 * {@link WriteStream}.
 * <p>
 * When the {@link WriteStream} has processed half of its backlog, the {@code drainHandler} will be
 * called, which results in the pump resuming the {@link ReadStream}.
 * <p>
 * This class can be used to pipe from any {@link ReadStream} to any {@link WriteStream},
 * e.g. from an {@link io.vertx.core.http.HttpServerRequest} to an {@link io.vertx.core.file.AsyncFile},
 * or from {@link io.vertx.core.net.NetSocket} to a {@link io.vertx.core.http.WebSocket}.
 * <p>
 * Please see the documentation for more information.
 */
@VertxGen
public interface Pipe<T> {

  /**
   * Set to {@code true} to call {@link WriteStream#end()} when the source {@code ReadStream} fails, {@code false} otherwise.
   *
   * @param end {@code true} to end the stream on a source {@code ReadStream} failure
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  Pipe<T> endOnFailure(boolean end);

  /**
   * Set to {@code true} to call {@link WriteStream#end()} when the source {@code ReadStream} succeeds, {@code false} otherwise.
   *
   * @param end {@code true} to end the stream on a source {@code ReadStream} success
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  Pipe<T> endOnSuccess(boolean end);

  /**
   * Set to {@code true} to call {@link WriteStream#end()} when the source {@code ReadStream} completes, {@code false} otherwise.
   * <p>
   * Calling this overwrites {@link #endOnFailure} and {@link #endOnSuccess}.
   *
   * @param end {@code true} to end the stream on a source {@code ReadStream} completion
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  Pipe<T> endOnComplete(boolean end);

  /**
   * Like {@link #to(WriteStream, Handler)} but without a completion handler
   */
  void to(WriteStream<T> dst);

  /**
   * Start to pipe the elements to the destination {@code WriteStream}.
   * <p>
   * When the operation fails with a write error, the source stream is resumed.
   *
   * @param dst the destination write stream
   * @param completionHandler the handler called when the pipe operation completes
   */
  void to(WriteStream<T> dst, Handler<AsyncResult<Void>> completionHandler);

  /**
   * Close the pipe.
   * <p>
   * The streams handlers will be unset and the read stream resumed unless it is already ended.
   */
  void close();

}
