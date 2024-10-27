/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
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
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Represents a file on the file-system which can be read from, or written to asynchronously.
 * <p>
 * This class also implements {@link io.vertx.core.streams.ReadStream} and
 * {@link io.vertx.core.streams.WriteStream}. This allows the data to be piped to and from
 * other streams, e.g. an {@link io.vertx.core.http.HttpClientRequest} instance,
 * using the {@link io.vertx.core.streams.Pipe} class
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
  AsyncFile setWriteQueueMaxSize(int maxSize);

  @Override
  AsyncFile drainHandler(Handler<Void> handler);

  @Override
  AsyncFile exceptionHandler(Handler<Throwable> handler);

  @Override
  AsyncFile fetch(long amount);

  /**
   * Close the file. The actual close happens asynchronously.
   *
   * @return a future completed with the result
   */
  Future<Void> close();

  /**
   * Write a {@link io.vertx.core.buffer.Buffer} to the file at position {@code position} in the file, asynchronously.
   * <p>
   * If {@code position} lies outside of the current size
   * of the file, the file will be enlarged to encompass it.
   * <p>
   * When multiple writes are invoked on the same file
   * there are no guarantees as to order in which those writes actually occur
   *
   * @param buffer  the buffer to write
   * @param position  the position in the file to write it at
   * @return a future notified when the write is complete
   */
  Future<Void> write(Buffer buffer, long position);

  /**
   * Reads {@code length} bytes of data from the file at position {@code position} in the file, asynchronously.
   * <p>
   * The read data will be written into the specified {@code Buffer buffer} at position {@code offset}.
   * <p>
   * If data is read past the end of the file then zero bytes will be read.<p>
   * When multiple reads are invoked on the same file there are no guarantees as to order in which those reads actually occur.
   *
   * @param buffer  the buffer to read into
   * @param offset  the offset into the buffer where the data will be read
   * @param position  the position in the file where to start reading
   * @param length  the number of bytes to read
   * @return a future notified when the write is complete
   */
  Future<Buffer> read(Buffer buffer, int offset, long position, int length);

  /**
   * Flush any writes made to this file to underlying persistent storage.
   * <p>
   * If the file was opened with {@code flush} set to {@code true} then calling this method will have no effect.
   * <p>
   * The actual flush will happen asynchronously.
   *
   * @return a future completed with the result
   */
  Future<Void> flush();

  /**
   * Sets the position from which data will be read from when using the file as a {@link io.vertx.core.streams.ReadStream}.
   *
   * @param readPos  the position in the file
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  AsyncFile setReadPos(long readPos);

  /**
   * Sets the number of bytes that will be read when using the file as a {@link io.vertx.core.streams.ReadStream}.
   *
   * @param readLength the bytes that will be read from the file
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  AsyncFile setReadLength(long readLength);

  /**
   * @return the number of bytes that will be read when using the file as a {@link io.vertx.core.streams.ReadStream}
   */
  long getReadLength();

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

  /**
   * Like {@link #size()} but blocking.
   *
   * @throws FileSystemException if an error occurs
   */
  long sizeBlocking();

  /**
   * @return the size of the file
   */
  Future<Long> size();

  /**
   * Try to acquire a non-shared lock on the entire file.
   *
   * @return the lock if it can be acquired immediately, otherwise {@code null}
   */
  default @Nullable AsyncFileLock tryLock() {
    return tryLock(0, Long.MAX_VALUE, false);
  }

  /**
   * Try to acquire a lock on a portion of this file.
   *
   * @param position where the region starts
   * @param size the size of the region
   * @param shared whether the lock should be shared
   * @return the lock if it can be acquired immediately, otherwise {@code null}
   */
  @Nullable AsyncFileLock tryLock(long position, long size, boolean shared);

  /**
   * Acquire a non-shared lock on the entire file.
   *
   * @return a future indicating the completion of this operation
   */
  default Future<AsyncFileLock> lock() {
    return lock(0, Long.MAX_VALUE, false);
  }

  /**
   * Acquire a lock on a portion of this file.
   *
   * @param position where the region starts
   * @param size the size of the region
   * @param shared whether the lock should be shared
   * @return a future indicating the completion of this operation
   */
  Future<AsyncFileLock> lock(long position, long size, boolean shared);

  /**
   * Acquire a non-shared lock on the entire file.
   *
   * <p>When the {@code block} is called, the lock is already acquired, it will be released when the
   * {@code Future<T>} returned by the block completes.
   *
   * <p>When the {@code block} fails, the lock is released and the returned future is failed with the cause of the failure.
   *
   * @param block the code block called after lock acquisition
   * @return the future returned by the {@code block}
   */
  default <T> Future<T> withLock(Supplier<Future<T>> block) {
    return withLock(0, Long.MAX_VALUE, false, block);
  }

  /**
   * Acquire a lock on a portion of this file.
   *
   * <p>When the {@code block} is called, the lock is already acquired , it will be released when the
   * {@code Future<T>} returned by the block completes.
   *
   * <p>When the {@code block} fails, the lock is released and the returned future is failed with the cause of the failure.
   *
   * @param position where the region starts
   * @param size the size of the region
   * @param shared whether the lock should be shared
   * @param block the code block called after lock acquisition
   * @return the future returned by the {@code block}
   */
  default <T> Future<T> withLock(long position, long size, boolean shared, Supplier<Future<T>> block) {
    return lock(position, size, shared)
      .compose(lock -> {
        Future<T> res;
        try {
          res = block.get();
        } catch (Exception e) {
          lock.release();
          throw new VertxException(e);
        }
        return res.eventually(() -> lock.release());
      });
  }
}
