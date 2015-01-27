/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
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
  WebSocket writeMessage(Buffer data);

  @Override
  WebSocket closeHandler(Handler<Void> handler);

  @Override
  WebSocket frameHandler(Handler<WebSocketFrame> handler);
}
