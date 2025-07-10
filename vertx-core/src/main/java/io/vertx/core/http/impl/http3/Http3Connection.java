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
package io.vertx.core.http.impl.http3;

import io.netty.buffer.ByteBuf;
import io.netty.handler.stream.ChunkedInput;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.vertx.core.Promise;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.internal.ContextInternal;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public interface Http3Connection {

  Http3HeadersMultiMap newHeaders();

  ContextInternal context();

  boolean isSsl();

  void reportBytesWritten(long numOfBytes);

  void flushBytesWritten();

  void reportBytesRead(long numOfBytes);

  void flushBytesRead();

  void writeFrame(QuicStreamChannel streamChannel, int type, int flags, ByteBuf payload, Promise<Void> promise);

  void writeHeaders(QuicStreamChannel streamChannel, Http3HeadersMultiMap headers, StreamPriorityBase priority, boolean end, boolean checkFlush, Promise<Void> promise);

  void writeData(QuicStreamChannel streamChannel, ByteBuf buf, boolean end, Promise<Void> promise);

  void writeReset(QuicStreamChannel streamChannel, long code, Promise<Void> promise);

  void writePriorityFrame(QuicStreamChannel streamChannel, StreamPriorityBase priority);

  void consumeCredits(QuicStreamChannel streamChannel, int amountOfBytes);

  boolean supportsSendFile();

  void sendFile(QuicStreamChannel streamChannel, ChunkedInput<ByteBuf> file, Promise<Void> promise);

}
