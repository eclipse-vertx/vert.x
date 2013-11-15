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

package org.vertx.java.core.sockjs;

import org.vertx.java.core.MultiMap;
import org.vertx.java.core.streams.ReadStream;
import org.vertx.java.core.streams.WriteStream;

import java.net.InetSocketAddress;

/**
 *
 * You interact with SockJS clients through instances of SockJS socket.<p>
 * The API is very similar to {@link org.vertx.java.core.http.WebSocket}.
 * It implements both {@link ReadStream} and {@link WriteStream} so it can be used with
 * {@link org.vertx.java.core.streams.Pump} to pump data with flow control.<p>
 * Instances of this class are not thread-safe.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface SockJSSocket extends ReadStream<SockJSSocket>, WriteStream<SockJSSocket> {

  /**
   * When a {@code SockJSSocket} is created it automatically registers an event handler with the event bus, the ID of that
   * handler is given by {@code writeHandlerID}.<p>
   * Given this ID, a different event loop can send a buffer to that event handler using the event bus and
   * that buffer will be received by this instance in its own event loop and written to the underlying socket. This
   * allows you to write data to other sockets which are owned by different event loops.
   */
  String writeHandlerID();

  /**
   * Close it
   */
  void close();

  /**
   * Return the remote address for this socket
   */
  InetSocketAddress remoteAddress();

  /**
   * Return the local address for this socket
   */
  InetSocketAddress localAddress();

  /**
   * Return the headers corresponding to the last request for this socket or the websocket handshake
   * Any cookie headers will be removed for security reasons
   */
  MultiMap headers();

  /**
   * Return the URI corresponding to the last request for this socket or the websocket handshake
   */
  String uri();
}
