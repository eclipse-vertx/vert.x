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
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.impl.ContextInternal;

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

  /**
   * @return the stream version or null if it's not yet determined
   */
  HttpVersion version();

  HttpConnection connection();
  ContextInternal getContext();

  void writeHead(HttpMethod method, String uri, MultiMap headers, String authority, boolean chunked, ByteBuf buf, boolean end, StreamPriority priority, Handler<AsyncResult<Void>> listener);
  void writeBuffer(ByteBuf buf, boolean end, Handler<AsyncResult<Void>> listener);
  void writeFrame(int type, int flags, ByteBuf payload);

  void doSetWriteQueueMaxSize(int size);
  boolean isNotWritable();
  void doPause();
  void doFetch(long amount);

  void reset(Throwable cause);

  StreamPriority priority();
  void updatePriority(StreamPriority streamPriority);
}
