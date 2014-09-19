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
 * Represents an HTML 5 Websocket<p>
 * Instances of this class are created and provided to the handler of an
 * {@link HttpClient} when a successful websocket connect attempt occurs.<p>
 * On the server side, the subclass {@link ServerWebSocket} is used instead.<p>
 * It implements both {@link ReadStream} and {@link WriteStream} so it can be used with
 * {@link io.vertx.core.streams.Pump} to pump data with flow control.<p>
 * Instances of this class are not thread-safe<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen(concrete = false)
public interface WebSocketBase<T> extends ReadStream<T, Buffer>, WriteStream<T, Buffer> {

  /**
   * When a {@code Websocket} is created it automatically registers an event handler with the eventbus, the ID of that
   * handler is given by {@code binaryHandlerID}.<p>
   * Given this ID, a different event loop can send a binary frame to that event handler using the event bus and
   * that buffer will be received by this instance in its own event loop and written to the underlying connection. This
   * allows you to write data to other websockets which are owned by different event loops.
   */
  String binaryHandlerID();

  /**
   * When a {@code Websocket} is created it automatically registers an event handler with the eventbus, the ID of that
   * handler is given by {@code textHandlerID}.<p>
   * Given this ID, a different event loop can send a text frame to that event handler using the event bus and
   * that buffer will be received by this instance in its own event loop and written to the underlying connection. This
   * allows you to write data to other websockets which are owned by different event loops.
   */
  String textHandlerID();

  @Fluent
  T writeFrame(WebSocketFrame frame);


  /*
  OK - get rid of above writeBinaryFrame/writeTextFrame and replace with writeFrame which takes a websocket frame
  allow websocket frame to be instantiated

  Also provide a method to write a single complete websocket message

  For reading provide a method to read a single websocket message, like a body handler
   */

  /**
   * Writes a (potentially large) piece of data as a websocket message - this may be split into multiple frames
   * if it is large.
   */
  @Fluent
  T writeMessage(Buffer data);

  /**
   * Set a closed handler on the connection
   */
  @Fluent
  T closeHandler(Handler<Void> handler);

  /**
   * Set a frame handler on the connection
   */
  @Fluent
  T frameHandler(Handler<WebSocketFrame> handler);

  /**
   * Close the websocket
   */
  void close();

  /**
   * Return the remote address for this socket
   */
  @CacheReturn
  SocketAddress remoteAddress();

  /**
   * Return the local address for this socket
   */
  @CacheReturn
  SocketAddress localAddress();

}
