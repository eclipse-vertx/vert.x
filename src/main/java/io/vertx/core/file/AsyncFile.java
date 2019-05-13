/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.file;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

/**
 * Represents a file on the file-system which can be read from, or written to asynchronously.
 * <p>
 * This class also implements {@link io.vertx.core.streams.ReadStream} and
 * {@link io.vertx.core.streams.WriteStream}. This allows the data to be pumped to and from
 * other streams, e.g. an {@link io.vertx.core.http.HttpClientRequest} instance,
 * using the {@link io.vertx.core.streams.Pump} class
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface AsyncFile extends ReadStream<Buffer>, WriteStream<Buffer> {

  @Override
  AsyncFile handler(Handler<Buffer> handler);

  @Override
  AsyncFile pause();

  @Override
  AsyncFile resume();

  @Override
  AsyncFile endHandler(Handler<Void> endHandler);

  @Override
  AsyncFile write(Buffer data);

  /**
   * Same as {@link #write(Buffer)} but with an {@code handler} called when the operation completes
   */
  @Fluent
  AsyncFile write(Buffer data, Handler<AsyncResult<Void>> handler);

  @Override
  AsyncFile setWriteQueueMaxSize(int maxSize);

  @Override
  AsyncFile drainHandler(Handler<Void> handler);

  @Override
  AsyncFile exceptionHandler(Handler<Throwable> handler);

  @Override
  AsyncFile fetch(long amount);

  /**
   * Close the file, see {@link #close()}.
   */
  @Override
  void end();

  /**
   * Close the file, see {@link #close(Handler)}.
   */
  @Override
  void end(Handler<AsyncResult<Void>> handler);

  /**
   * Close the file. The actual close happens asynchronously.
   */
  void close();

  /**
   * Close the file. The actual close happens asynchronously.
   * The handler will be called when the close is complete, or an error occurs.
   *
   * @param handler  the handler
   */
  void close(Handler<AsyncResult<Void>> handler);

  /**
   * Write a {@link io.vertx.core.buffer.Buffer} to the file at position {@code position} in the file, asynchronously.
   * <p>
   * If {@code position} lies outside of the current size
   * of the file, the file will be enlarged to encompass it.
   * <p>
   * When multiple writes are invoked on the same file
   * there are no guarantees as to order in which those writes actually occur
   * <p>
   * The handler will be called when the write is complete, or if an error occurs.
   *
   * @param buffer  the buffer to write
   * @param position  the position in the file to write it at
   * @param handler  the handler to call when the write is complete
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  AsyncFile write(Buffer buffer, long position, Handler<AsyncResult<Void>> handler);

  /**
   * Reads {@code length} bytes of data from the file at position {@code position} in the file, asynchronously.
   * <p>
   * The read data will be written into the specified {@code Buffer buffer} at position {@code offset}.
   * <p>
   * If data is read past the end of the file then zero bytes will be read.<p>
   * When multiple reads are invoked on the same file there are no guarantees as to order in which those reads actually occur.
   * <p>
   * The handler will be called when the close is complete, or if an error occurs.
   *
   * @param buffer  the buffer to read into
   * @param offset  the offset into the buffer where the data will be read
   * @param position  the position in the file where to start reading
   * @param length  the number of bytes to read
   * @param handler  the handler to call when the write is complete
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  AsyncFile read(Buffer buffer, int offset, long position, int length, Handler<AsyncResult<Buffer>> handler);

  /**
   * Flush any writes made to this file to underlying persistent storage.
   * <p>
   * If the file was opened with {@code flush} set to {@code true} then calling this method will have no effect.
   * <p>
   * The actual flush will happen asynchronously.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  AsyncFile flush();

  /**
   * Same as {@link #flush} but the handler will be called when the flush is complete or if an error occurs
   */
  @Fluent
  AsyncFile flush(Handler<AsyncResult<Void>> handler);

  /**
   * Sets the position from which data will be read from when using the file as a {@link io.vertx.core.streams.ReadStream}.
   *
   * @param readPos  the position in the file
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  AsyncFile setReadPos(long readPos);

  /**
   * Sets the position from which data will be written when using the file as a {@link io.vertx.core.streams.WriteStream}.
   *
   * @param writePos  the position in the file
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  AsyncFile setWritePos(long writePos);

  /**
   * @return the current write position the file is at
   */
  long getWritePos();

  /**
   * Sets the buffer size that will be used to read the data from the file. Changing this value will impact how much
   * the data will be read at a time from the file system.
   * @param readBufferSize the buffer size
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  AsyncFile setReadBufferSize(int readBufferSize);
}
