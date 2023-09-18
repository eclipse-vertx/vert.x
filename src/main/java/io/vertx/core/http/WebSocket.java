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

package io.vertx.core.http;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

/**
 * Common WebSocket implementation.
 * <p>
 * It implements both {@link ReadStream} and {@link WriteStream} so it can be used with
 * {@link io.vertx.core.streams.Pipe} to pipe data with flow control.
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
  WebSocket setWriteQueueMaxSize(int maxSize);

  @Override
  WebSocket drainHandler(Handler<Void> handler);

  @Override
  WebSocket closeHandler(Handler<Void> handler);

  @Override
  WebSocket frameHandler(Handler<WebSocketFrame> handler);

  @Override
  WebSocket textMessageHandler(@Nullable Handler<String> handler);

  @Override
  WebSocket binaryMessageHandler(@Nullable Handler<Buffer> handler);

  @Override
  WebSocket pongHandler(@Nullable Handler<Buffer> handler);

}
