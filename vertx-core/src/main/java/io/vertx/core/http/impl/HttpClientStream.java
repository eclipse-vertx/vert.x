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

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpClientStream extends HttpStream {

  Object trace();

  HttpClientConnection connection();

  Future<Void> writeHead(HttpRequestHead request, boolean chunked, Buffer buf, boolean end, StreamPriority priority, boolean connect);

  HttpClientStream headHandler(Handler<HttpResponseHead> handler);
  HttpClientStream resetHandler(Handler<Long> handler);
  HttpClientStream exceptionHandler(Handler<Throwable> handler);
  HttpClientStream continueHandler(Handler<Void> handler);
  HttpClientStream earlyHintsHandler(Handler<MultiMap> handler);
  HttpClientStream pushHandler(Handler<Http2ClientPush> handler);
  HttpClientStream customFrameHandler(Handler<HttpFrame> handler);
  HttpClientStream dataHandler(Handler<Buffer> handler);
  HttpClientStream trailersHandler(Handler<MultiMap> handler);
  HttpClientStream priorityChangeHandler(Handler<StreamPriority> handler);
  HttpClientStream closeHandler(Handler<Void> handler);
  HttpClientStream drainHandler(Handler<Void> handler);

  HttpClientStream setWriteQueueMaxSize(int maxSize);
  HttpClientStream pause();
  HttpClientStream fetch(long amount);

  HttpClientStream updatePriority(StreamPriority streamPriority);

}
