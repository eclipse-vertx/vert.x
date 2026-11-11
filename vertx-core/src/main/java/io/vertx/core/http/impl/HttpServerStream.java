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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.stream.ChunkedInput;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.streams.WriteResult;
import io.vertx.core.net.HostAndPort;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpServerStream extends HttpStream {

  HttpServerStream routed(String route);

  HttpServerConnection connection();

  /**
   * Set the head of the stream, it is written by the next content operation: alone for an empty write, with the
   * end of the stream for a bodyless end.
   */
  HttpServerStream head(HttpResponseHead head);

  /**
   * Write the head alone, as {@link #head} followed by an empty write.
   */
  default WriteResult writeHead(HttpResponseHead head) {
    return head(head).write(BufferInternal.buffer(Unpooled.EMPTY_BUFFER));
  }

  Future<HttpServerStream> sendPush(HostAndPort authority, HttpMethod method, MultiMap headers, String path, StreamPriority priority);

  HttpServerStream headHandler(Handler<HttpRequestHead> handler);
  HttpServerStream resetHandler(Handler<Long> handler);
  HttpServerStream exceptionHandler(Handler<Throwable> handler);
  HttpServerStream customFrameHandler(Handler<HttpFrame> handler);
  HttpServerStream trailersHandler(Handler<MultiMap> handler);
  HttpServerStream priorityChangeHandler(Handler<StreamPriority> handler);
  HttpServerStream closeHandler(Handler<Void> handler);
  HttpServerStream drainHandler(Handler<Void> handler);

  HttpServerStream setWriteQueueMaxSize(int maxSize);

  void sendFile(ChunkedInput<ByteBuf> file, Promise<Void> promise);


  HttpServerStream updatePriority(StreamPriority streamPriority);

}
