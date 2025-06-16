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
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.core.Promise;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.internal.ContextInternal;

interface Http2Connection {

  Http2HeadersAdaptor newHeaders();

  ContextInternal context();

  boolean isSsl();

  boolean isWritable(VertxHttp2Stream stream);

  void reportBytesWritten(long numOfBytes);

  void flushBytesWritten();

  void reportBytesRead(long numOfBytes);

  void flushBytesRead();

  void writeFrame(VertxHttp2Stream stream, int type, int flags, ByteBuf payload, Promise<Void> promise);

  void writeHeaders(VertxHttp2Stream stream, Http2Headers headers, StreamPriority priority, boolean end, boolean checkFlush, Promise<Void> promise);

  void writeData(VertxHttp2Stream stream, ByteBuf buf, boolean end, Promise<Void> promise);

  void writeReset(VertxHttp2Stream stream, long code, Promise<Void> promise);

  void writePriorityFrame(VertxHttp2Stream stream, StreamPriority priority);

  void consumeCredits(VertxHttp2Stream stream, int amountOfBytes);

}
