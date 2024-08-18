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

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.streams.WriteStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpClientStream extends WriteStream<Buffer> {

  /**
   * @return the stream id, {@code 1} denotes the first stream, HTTP/1 is a simple sequence, HTTP/2
   * is the actual stream identifier.
   */
  int id();

  Object metric();

  Object trace();

  /**
   * @return the stream version or null if it's not yet determined
   */
  HttpVersion version();

  HttpClientConnection connection();
  ContextInternal getContext();

  void writeHead(HttpRequestHead request,
                 boolean chunked,
                 ByteBuf buf,
                 boolean end,
                 StreamPriorityBase priority,
                 boolean connect,
                 Handler<AsyncResult<Void>> handler);
  void writeBuffer(ByteBuf buf, boolean end, Handler<AsyncResult<Void>> listener);
  void writeFrame(int type, int flags, ByteBuf payload);

  void continueHandler(Handler<Void> handler);
  void earlyHintsHandler(Handler<MultiMap> handler);
  void pushHandler(Handler<HttpClientPush> handler);
  void unknownFrameHandler(Handler<HttpFrame> handler);

  @Override
  default Future<Void> write(Buffer data) {
    PromiseInternal<Void> promise = getContext().promise();
    writeBuffer(data.getByteBuf(), false, promise);
    return promise.future();
  }

  @Override
  default void write(Buffer data, Handler<AsyncResult<Void>> handler) {
    writeBuffer(data.getByteBuf(), false, handler);
  }

  @Override
  default Future<Void> end(Buffer data) {
    PromiseInternal<Void> promise = getContext().promise();
    writeBuffer(data.getByteBuf(), true, promise);
    return promise.future();
  }

  @Override
  default void end(Buffer data, Handler<AsyncResult<Void>> handler) {
    writeBuffer(data.getByteBuf(), true, handler);
  }

  @Override
  default Future<Void> end() {
    PromiseInternal<Void> promise = getContext().promise();
    writeBuffer(Unpooled.EMPTY_BUFFER, true, promise);
    return promise.future();
  }

  @Override
  default void end(Handler<AsyncResult<Void>> handler) {
    writeBuffer(Unpooled.EMPTY_BUFFER, true, handler);
  }

  void headHandler(Handler<HttpResponseHead> handler);
  void chunkHandler(Handler<Buffer> handler);
  void endHandler(Handler<MultiMap> handler);
  void priorityHandler(Handler<StreamPriorityBase> handler);
  void closeHandler(Handler<Void> handler);

  void doSetWriteQueueMaxSize(int size);
  boolean isNotWritable();
  void doPause();
  void doFetch(long amount);

  void reset(Throwable cause);

  StreamPriorityBase priority();
  void updatePriority(StreamPriorityBase streamPriority);

}
