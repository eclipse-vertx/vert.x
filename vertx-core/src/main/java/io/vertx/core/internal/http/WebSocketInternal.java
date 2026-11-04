/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.http;

import io.netty.channel.ChannelHandlerContext;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.internal.streams.WriteResult;
import io.vertx.core.internal.streams.WriteStreamInternal;

public interface WebSocketInternal extends WebSocket, WriteStreamInternal<Buffer> {

  /**
   * @return the Netty channel handler context
   */
  ChannelHandlerContext channelHandlerContext();

  @Override
  WriteResult write(Buffer data);

  @Override
  WriteResult writeFrame(WebSocketFrame frame);

  @Override
  WriteResult writeFinalTextFrame(String text);

  @Override
  WriteResult writeFinalBinaryFrame(Buffer data);

  @Override
  WriteResult writeBinaryMessage(Buffer data);

  @Override
  WriteResult writeTextMessage(String text);

  @Override
  WriteResult writePing(Buffer data);

  @Override
  WriteResult writePong(Buffer data);

  @Override
  WebSocketInternal setWriteQueueMaxSize(int maxSize);

  @Override
  WebSocketInternal drainHandler(Handler<Void> handler);

  @Override
  WebSocketInternal exceptionHandler(Handler<Throwable> handler);

}
