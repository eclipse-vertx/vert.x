/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

/**
 * Represents a client-side WebSocket.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface WebSocket extends WebSocketBase {

  @Override
  WebSocket exceptionHandler(Handler<Throwable> handler);

  @Override
  WebSocket handler(Handler<Buffer> handler);

  @Override
  WebSocket pause();

  @Override
  WebSocket resume();

  @Override
  WebSocket fetch(long amount);

  @Override
  WebSocket endHandler(Handler<Void> endHandler);

  @Override
  WebSocket write(Buffer data);

  @Override
  WebSocket setWriteQueueMaxSize(int maxSize);

  @Override
  WebSocket drainHandler(Handler<Void> handler);

  @Override
  WebSocket writeFrame(WebSocketFrame frame);

  @Override
  WebSocket writeFinalTextFrame(String text);

  @Override
  WebSocket writeFinalBinaryFrame(Buffer data);

  @Override
  WebSocket writeBinaryMessage(Buffer data);

  @Override
  WebSocket writeTextMessage(String text);

  @Override
  WebSocket closeHandler(Handler<Void> handler);

  @Override
  WebSocket frameHandler(Handler<WebSocketFrame> handler);
}
