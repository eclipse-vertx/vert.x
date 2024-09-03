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
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
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

  HttpClientConnectionInternal connection();
  ContextInternal getContext();

  Future<Void> writeHead(HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, StreamPriorityBase priority, boolean connect);
  Future<Void> writeBuffer(ByteBuf buf, boolean end);
  Future<Void> writeFrame(int type, int flags, ByteBuf payload);

  void continueHandler(Handler<Void> handler);
  void earlyHintsHandler(Handler<MultiMap> handler);
  void pushHandler(Handler<HttpClientPush> handler);
  void unknownFrameHandler(Handler<HttpFrame> handler);

  @Override
  default Future<Void> write(Buffer data) {
    return writeBuffer(((BufferInternal)data).getByteBuf(), false);
  }

  @Override
  default Future<Void> end(Buffer data) {
    return writeBuffer(((BufferInternal)data).getByteBuf(), true);
  }

  @Override
  default Future<Void> end() {
    return writeBuffer(Unpooled.EMPTY_BUFFER, true);
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

  Future<Void> reset(Throwable cause);

  StreamPriorityBase priority();
  void updatePriority(StreamPriorityBase streamPriority);
  StreamPriorityBase createDefaultStreamPriority();
}
