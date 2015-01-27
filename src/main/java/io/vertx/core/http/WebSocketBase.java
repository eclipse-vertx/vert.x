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

import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

/**
 * Base WebSocket implementation.
 * <p>
 * It implements both {@link ReadStream} and {@link WriteStream} so it can be used with
 * {@link io.vertx.core.streams.Pump} to pump data with flow control.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen(concrete = false)
public interface WebSocketBase extends ReadStream<Buffer>, WriteStream<Buffer> {

  @Override
  WebSocketBase exceptionHandler(Handler<Throwable> handler);

  @Override
  WebSocketBase handler(Handler<Buffer> handler);

  @Override
  WebSocketBase pause();

  @Override
  WebSocketBase resume();

  @Override
  WebSocketBase endHandler(Handler<Void> endHandler);

  @Override
  WebSocketBase write(Buffer data);

  @Override
  WebSocketBase setWriteQueueMaxSize(int maxSize);

  @Override
  WebSocketBase drainHandler(Handler<Void> handler);

  /**
   * When a {@code Websocket} is created it automatically registers an event handler with the event bus - the ID of that
   * handler is given by this method.
   * <p>
   * Given this ID, a different event loop can send a binary frame to that event handler using the event bus and
   * that buffer will be received by this instance in its own event loop and written to the underlying connection. This
   * allows you to write data to other WebSockets which are owned by different event loops.
   *
   * @return the binary handler id
   */
  String binaryHandlerID();

  /**
   * When a {@code Websocket} is created it automatically registers an event handler with the eventbus, the ID of that
   * handler is given by {@code textHandlerID}.
   * <p>
   * Given this ID, a different event loop can send a text frame to that event handler using the event bus and
   * that buffer will be received by this instance in its own event loop and written to the underlying connection. This
   * allows you to write data to other WebSockets which are owned by different event loops.
   */
  String textHandlerID();

  /**
   * Write a WebSocket frame to the connection
   *
   * @param frame  the frame to write
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  WebSocketBase writeFrame(WebSocketFrame frame);

  /**
   * Writes a (potentially large) piece of data to the connection. This data might be written as multiple frames
   * if it exceeds the maximum WebSocket frame size.
   *
   * @param data  the data to write
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  WebSocketBase writeMessage(Buffer data);

  /**
   * Set a close handler. This will be called when the WebSocket is closed.
   *
   * @param handler  the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  WebSocketBase closeHandler(Handler<Void> handler);

  /**
   * Set a frame handler on the connection. This handler will be called when frames are read on the connection.
   *
   * @param handler  the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  WebSocketBase frameHandler(Handler<WebSocketFrame> handler);

  /**
   * Close the WebSocket.
   */
  void close();

  /**
   * @return the remote address for this socket
   */
  @CacheReturn
  SocketAddress remoteAddress();

  /**
   * @return the local address for this socket
   */
  @CacheReturn
  SocketAddress localAddress();

}
