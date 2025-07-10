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
package io.vertx.core.http.impl.http2;

import io.netty.buffer.ByteBuf;
import io.netty.handler.stream.ChunkedInput;
import io.vertx.core.Promise;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.internal.ContextInternal;

public interface Http2Connection {

  Http2HeadersMultiMap newHeaders();

  ContextInternal context();

  boolean isSsl();

  void reportBytesWritten(long numOfBytes);

  void flushBytesWritten();

  void reportBytesRead(long numOfBytes);

  void flushBytesRead();

  void writeFrame(int streamId, int type, int flags, ByteBuf payload, Promise<Void> promise);

  void writeHeaders(int streamId, Http2HeadersMultiMap headers, StreamPriorityBase priority, boolean end, boolean checkFlush, Promise<Void> promise);

  void writeData(int streamId, ByteBuf buf, boolean end, Promise<Void> promise);

  void writeReset(int streamId, long code, Promise<Void> promise);

  void writePriorityFrame(int streamId, StreamPriorityBase priority);

  void consumeCredits(int streamId, int amountOfBytes);

  boolean supportsSendFile();

  void sendFile(int streamId, ChunkedInput<ByteBuf> file, Promise<Void> promise);

}
