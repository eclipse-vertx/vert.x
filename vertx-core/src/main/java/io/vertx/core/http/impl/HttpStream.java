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

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.internal.ContextInternal;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpStream {

  /**
   * @return the stream id, {@code 1} denotes the first stream, HTTP/1 is a simple sequence, HTTP/2
   * is the actual stream identifier.
   */
  int id();

  /**
   * @return the stream version or null if it's not yet determined
   */
  HttpVersion version();

  Object metric();

  HttpConnection connection();
  ContextInternal context();

  Future<Void> writeChunk(Buffer buf, boolean end);
  Future<Void> writeFrame(int type, int flags, Buffer payload);
  Future<Void> writeReset(long code);

  Future<Boolean> cancel();

  HttpStream resetHandler(Handler<Long> handler);
  HttpStream exceptionHandler(Handler<Throwable> handler);
  HttpStream customFrameHandler(Handler<HttpFrame> handler);
  HttpStream dataHandler(Handler<Buffer> handler);
  HttpStream trailersHandler(Handler<MultiMap> handler);
  HttpStream priorityChangeHandler(Handler<StreamPriority> handler);
  HttpStream closeHandler(Handler<Void> handler);
  HttpStream drainHandler(Handler<Void> handler);

  boolean isWritable();

  HttpStream setWriteQueueMaxSize(int maxSize);
  HttpStream pause();
  HttpStream fetch(long amount);

  StreamPriority priority();
  HttpStream updatePriority(StreamPriority streamPriority);

}
