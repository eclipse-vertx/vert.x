/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

/**
 * Represents a socket-like interface on either the client or the server side.
 * <p>
 * It implements both {@link ReadStream} and {@link WriteStream} so it can be used with
 * {@link io.vertx.core.streams.Pipe} to pipe data with flow control.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen(concrete = false)
public interface Socket extends ReadStream<Buffer>, WriteStream<Buffer> {

  @Override
  Socket exceptionHandler(Handler<Throwable> handler);

  @Override
  Socket handler(Handler<Buffer> handler);

  @Override
  Socket pause();

  @Override
  Socket resume();

  @Override
  Socket fetch(long amount);

  /**
   * {@inheritDoc}
   * <p>
   * This handler might be called after the close handler when the socket is paused and there are still
   * buffers to deliver.
   */
  @Override
  Socket endHandler(Handler<Void> endHandler);

  @Override
  Socket setWriteQueueMaxSize(int maxSize);

  @Override
  Socket drainHandler(Handler<Void> handler);

  /**
   * Write a {@link String} to the connection, encoded in UTF-8.
   *
   * @param str  the string to write
   * @return a future result of the write
   */
  Future<Void> write(String str);

  /**
   * Write a {@link String} to the connection, encoded using the encoding {@code enc}.
   *
   * @param str  the string to write
   * @param enc  the encoding to use
   * @return a future completed with the result
   */
  Future<Void> write(String str, String enc);

  /**
   * Tell the operating system to stream a file as specified by {@code filename} directly from disk to the outgoing connection,
   * bypassing userspace altogether (where supported by the underlying operating system. This is a very efficient way to stream files.
   *
   * @param filename  file name of the file to send
   * @return a future result of the send operation
   */
  default Future<Void> sendFile(String filename) {
    return sendFile(filename, 0, Long.MAX_VALUE);
  }

  /**
   * Tell the operating system to stream a file as specified by {@code filename} directly from disk to the outgoing connection,
   * bypassing userspace altogether (where supported by the underlying operating system. This is a very efficient way to stream files.
   *
   * @param filename  file name of the file to send
   * @param offset offset
   * @return a future result of the send operation
   */
  default Future<Void> sendFile(String filename, long offset) {
    return sendFile(filename, offset, Long.MAX_VALUE);
  }

  /**
   * Tell the operating system to stream a file as specified by {@code filename} directly from disk to the outgoing connection,
   * bypassing userspace altogether (where supported by the underlying operating system. This is a very efficient way to stream files.
   *
   * @param filename  file name of the file to send
   * @param offset offset
   * @param length length
   * @return a future result of the send operation
   */
  Future<Void> sendFile(String filename, long offset, long length);

  /**
   * Calls {@link #close()}
   *
   * @return a future completed with the result
   */
  @Override
  Future<Void> end();

  /**
   * Close the socket
   *
   * @return a future completed with the result
   */
  Future<Void> close();

  /**
   * Set a {@code handler} notified when the socket is closed
   *
   * @param handler  the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  Socket closeHandler(@Nullable Handler<Void> handler);

  /**
   * Set a {@code handler} notified when the socket is shutdown: the client or server will close the connection
   * within a certain amount of time. This gives the opportunity to the {@code handler} to close the socket gracefully before
   * the socket is closed.
   *
   * @param handler  the handler notified
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  Socket shutdownHandler(@Nullable Handler<Void> handler);

}
