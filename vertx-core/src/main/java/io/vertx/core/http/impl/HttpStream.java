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
package io.vertx.core.http.impl;

import io.netty.buffer.ByteBufAllocator;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpStream extends ReadStream<Buffer>, WriteStream<Buffer> {

  /**
   * @return the stream id, {@code 1} denotes the first stream, HTTP/1 is a simple sequence, HTTP/2
   * is the actual stream identifier.
   */
  long id();

  long bytesWritten();
  long bytesRead();
  Object metric();

  /**
   * @return the stream version or null if it's not yet determined
   */
  HttpVersion version();

  HttpConnection connection();
  ContextInternal context();
  ByteBufAllocator allocator();

  default Future<Void> write(Buffer buf) {
    return writeData(buf, false);
  }
  default Future<Void> end(Buffer buf) {
    return writeData(buf, true);
  }
  default Future<Void> end() {
    return writeData(Buffer.buffer(), true);
  }
  Future<Void> writeData(Buffer buf, boolean end);
  Future<Void> writeFrame(int type, int flags, Buffer payload);
  Future<Void> writeReset(long code);

  Future<Boolean> cancel();

  HttpStream resetHandler(Handler<Long> handler);
  HttpStream exceptionHandler(Handler<Throwable> handler);
  HttpStream customFrameHandler(Handler<HttpFrame> handler);
  HttpStream handler(Handler<Buffer> handler);
  default HttpStream endHandler(Handler<Void> handler) {
    throw new UnsupportedOperationException();
  }
  HttpStream trailersHandler(Handler<MultiMap> handler);
  HttpStream priorityChangeHandler(Handler<StreamPriority> handler);
  HttpStream closeHandler(Handler<Void> handler);
  HttpStream drainHandler(Handler<Void> handler);

  default HttpStream resume() {
    return fetch(Long.MAX_VALUE);
  }

  HttpStream setWriteQueueMaxSize(int maxSize);
  HttpStream pause();
  HttpStream fetch(long amount);

  StreamPriority priority();
  HttpStream updatePriority(StreamPriority streamPriority);

}
