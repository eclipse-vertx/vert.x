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

import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.streams.ReadStream;
import org.vertx.java.core.streams.WriteStream;

/**
 * Represents a file on the file-system which can be read from, or written to asynchronously.<p>
 * Methods also exist to get a {@link org.vertx.java.core.streams.ReadStream} or a
 * {@link org.vertx.java.core.streams.WriteStream} on the file. This allows the data to be pumped to and from
 * other streams, e.g. an {@link org.vertx.java.core.http.HttpClientRequest} instance,
 * using the {@link org.vertx.java.core.streams.Pump} class<p>
 * Instances of this class are not thread-safe<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface AsyncFile {

  public static final int BUFFER_SIZE = 8192;

  /**
   * Close the file. The actual close happens asynchronously.
   */
  void close();

  /**
   * Close the file. The actual close happens asynchronously.
   * The handler will be called when the close is complete, or an error occurs.
   */
  void close(AsyncResultHandler<Void> handler);

  /**
   * Write a {@link Buffer} to the file at position {@code position} in the file, asynchronously.
   * If {@code position} lies outside of the current size
   * of the file, the file will be enlarged to encompass it.<p>
   * When multiple writes are invoked on the same file
   * there are no guarantees as to order in which those writes actually occur.<p>
   * The handler will be called when the write is complete, or if an error occurs.
   */
  void write(Buffer buffer, int position, AsyncResultHandler<Void> handler);

  /**
   * Reads {@code length} bytes of data from the file at position {@code position} in the file, asynchronously.
   * The read data will be written into the specified {@code Buffer buffer} at position {@code offset}.<p>
   * The index {@code position + length} must lie within the confines of the file.<p>
   * When multiple reads are invoked on the same file there are no guarantees as to order in which those reads actually occur.<p>
   * The handler will be called when the close is complete, or if an error occurs.
   */
  void read(Buffer buffer, int offset, int position, int length, AsyncResultHandler<Buffer> handler);

  /**
   * Return a {@link WriteStream} instance operating on this {@code AsyncFile}.
   */
  WriteStream getWriteStream();

  /**
   * Return a {@code ReadStream} instance operating on this {@code AsyncFile}.
   */
  ReadStream getReadStream();

  /**
   * Flush any writes made to this file to underlying persistent storage.<p>
   * If the file was opened with {@code flush} set to {@code true} then calling this method will have no effect.<p>
   * The actual flush will happen asynchronously.
   */
  void flush();

  /**
   * Same as {@link #flush} but the handler will be called when the flush is complete or if an error occurs
   * @param handler
   */
  void flush(AsyncResultHandler<Void> handler);

}