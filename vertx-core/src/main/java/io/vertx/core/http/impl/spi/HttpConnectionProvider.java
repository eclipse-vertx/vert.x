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
package io.vertx.core.http.impl.spi;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.Headers;
import io.netty.handler.stream.ChunkedInput;
import io.vertx.core.Promise;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.internal.ContextInternal;

public interface HttpConnectionProvider {

  Http2HeadersMultiMap newHeaders();

  ContextInternal context();

  boolean isSsl();

  void reportBytesWritten(long numOfBytes);

  void flushBytesWritten();

  void reportBytesRead(long numOfBytes);

  void flushBytesRead();

  void writeFrame(int streamId, int type, int flags, ByteBuf payload, Promise<Void> promise);

  void writeHeaders(int streamId, Headers<CharSequence, CharSequence, ?> headers, StreamPriority priority, boolean end, boolean checkFlush, Promise<Void> promise);

  void writeData(int streamId, ByteBuf buf, boolean end, Promise<Void> promise);

  void writeReset(int streamId, long code, Promise<Void> promise);

  void writePriorityFrame(int streamId, StreamPriority priority, Promise<Void> promise);

  void sendFile(int streamId, ChunkedInput<ByteBuf> file, Promise<Void> promise);

  void consumeCredits(int streamId, int amountOfBytes);

}
