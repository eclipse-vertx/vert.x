/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.file;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.streams.ReadStream;
import org.vertx.java.core.streams.WriteStream;

/**
 * Represents a file on the file-system which can be read from, or written to asynchronously.<p>
 * This class also implements {@link org.vertx.java.core.streams.ReadStream} and
 * {@link org.vertx.java.core.streams.WriteStream}. This allows the data to be pumped to and from
 * other streams, e.g. an {@link org.vertx.java.core.http.HttpClientRequest} instance,
 * using the {@link org.vertx.java.core.streams.Pump} class<p>
 * Instances of AsyncFile are not thread-safe<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface AsyncFile extends ReadStream<AsyncFile>, WriteStream<AsyncFile> {

  /**
   * Close the file. The actual close happens asynchronously.
   */
  void close();

  /**
   * Close the file. The actual close happens asynchronously.
   * The handler will be called when the close is complete, or an error occurs.
   */
  void close(Handler<AsyncResult<Void>> handler);

  /**
   * Write a {@link Buffer} to the file at position {@code position} in the file, asynchronously.
   * If {@code position} lies outside of the current size
   * of the file, the file will be enlarged to encompass it.<p>
   * When multiple writes are invoked on the same file
   * there are no guarantees as to order in which those writes actually occur.<p>
   * The handler will be called when the write is complete, or if an error occurs.
   */
  AsyncFile write(Buffer buffer, int position, Handler<AsyncResult<Void>> handler);

  /**
   * Reads {@code length} bytes of data from the file at position {@code position} in the file, asynchronously.
   * The read data will be written into the specified {@code Buffer buffer} at position {@code offset}.<p>
   * If data is read past the end of the file then zero bytes will be read.<p>
   * When multiple reads are invoked on the same file there are no guarantees as to order in which those reads actually occur.<p>
   * The handler will be called when the close is complete, or if an error occurs.
   */
  AsyncFile read(Buffer buffer, int offset, int position, int length, Handler<AsyncResult<Buffer>> handler);

  /**
   * Flush any writes made to this file to underlying persistent storage.<p>
   * If the file was opened with {@code flush} set to {@code true} then calling this method will have no effect.<p>
   * The actual flush will happen asynchronously.
   */
  AsyncFile flush();

  /**
   * Same as {@link #flush} but the handler will be called when the flush is complete or if an error occurs
   */
  AsyncFile flush(Handler<AsyncResult<Void>> handler);

}