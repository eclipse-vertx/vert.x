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

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.http2.Http2ClientPush;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpClientStream {

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

  Future<Void> writeHead(HttpRequestHead request, boolean chunked, Buffer buf, boolean end, StreamPriority priority, boolean connect);
  Future<Void> write(Buffer buf, boolean end);
  Future<Void> writeFrame(int type, int flags, Buffer payload);
  Future<Void> writeReset(long code);

  HttpClientStream resetHandler(Handler<Long> handler);
  HttpClientStream exceptionHandler(Handler<Throwable> handler);
  HttpClientStream continueHandler(Handler<Void> handler);
  HttpClientStream earlyHintsHandler(Handler<MultiMap> handler);
  HttpClientStream pushHandler(Handler<Http2ClientPush> handler);
  HttpClientStream customFrameHandler(Handler<HttpFrame> handler);
  HttpClientStream headersHandler(Handler<HttpResponseHead> handler);
  HttpClientStream dataHandler(Handler<Buffer> handler);
  HttpClientStream trailersHandler(Handler<MultiMap> handler);
  HttpClientStream priorityChangeHandler(Handler<StreamPriority> handler);
  HttpClientStream closeHandler(Handler<Void> handler);
  HttpClientStream drainHandler(Handler<Void> handler);

  HttpClientStream setWriteQueueMaxSize(int maxSize);
  boolean isWritable();

  HttpClientStream pause();
  HttpClientStream fetch(long amount);

  StreamPriority priority();
  HttpClientStream updatePriority(StreamPriority streamPriority);

}
