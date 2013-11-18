/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
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
  AsyncFile write(Buffer buffer, long position, Handler<AsyncResult<Void>> handler);

  /**
   * Reads {@code length} bytes of data from the file at position {@code position} in the file, asynchronously.
   * The read data will be written into the specified {@code Buffer buffer} at position {@code offset}.<p>
   * If data is read past the end of the file then zero bytes will be read.<p>
   * When multiple reads are invoked on the same file there are no guarantees as to order in which those reads actually occur.<p>
   * The handler will be called when the close is complete, or if an error occurs.
   */
  AsyncFile read(Buffer buffer, int offset, long position, int length, Handler<AsyncResult<Buffer>> handler);

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