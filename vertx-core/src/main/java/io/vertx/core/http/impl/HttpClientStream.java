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
import io.vertx.core.http.impl.http2.Http2ClientPush;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpClientStream extends WriteStream<Buffer>, ReadStream<Buffer> {

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
  ContextInternal context();

  Future<Void> writeHead(HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, StreamPriority priority, boolean connect);
  Future<Void> write(ByteBuf buf, boolean end);
  Future<Void> writeFrame(int type, int flags, ByteBuf payload);

  @Override
  default Future<Void> write(Buffer data) {
    return write(((BufferInternal)data).getByteBuf(), false);
  }

  @Override
  default Future<Void> end(Buffer data) {
    return write(((BufferInternal)data).getByteBuf(), true);
  }

  @Override
  default Future<Void> end() {
    return write(Unpooled.EMPTY_BUFFER, true);
  }

  HttpClientStream exceptionHandler(Handler<Throwable> handler);
  HttpClientStream endHandler(Handler<Void> handler);
  HttpClientStream continueHandler(Handler<Void> handler);
  HttpClientStream earlyHintsHandler(Handler<MultiMap> handler);
  HttpClientStream pushHandler(Handler<Http2ClientPush> handler);
  HttpClientStream unknownFrameHandler(Handler<HttpFrame> handler);
  HttpClientStream headHandler(Handler<HttpResponseHead> handler);
  HttpClientStream handler(Handler<Buffer> handler);
  HttpClientStream trailersHandler(Handler<MultiMap> handler);
  HttpClientStream priorityHandler(Handler<StreamPriority> handler);
  HttpClientStream closeHandler(Handler<Void> handler);
  HttpClientStream drainHandler(Handler<Void> handler);

  HttpClientStream setWriteQueueMaxSize(int maxSize);

  HttpClientStream resume();
  HttpClientStream pause();
  HttpClientStream fetch(long amount);

  Future<Void> reset(Throwable cause);

  StreamPriority priority();
  HttpClientStream updatePriority(StreamPriority streamPriority);
}
