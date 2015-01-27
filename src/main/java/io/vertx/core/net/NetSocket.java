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

package io.vertx.core.net;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

/**
 * Represents a socket-like interface to a TCP connection on either the
 * client or the server side.
 * <p>
 * Instances of this class are created on the client side by an {@link NetClient}
 * when a connection to a server is made, or on the server side by a {@link NetServer}
 * when a server accepts a connection.
 * <p>
 * It implements both {@link ReadStream} and {@link WriteStream} so it can be used with
 * {@link io.vertx.core.streams.Pump} to pump data with flow control.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface NetSocket extends ReadStream<Buffer>, WriteStream<Buffer> {

  @Override
  NetSocket exceptionHandler(Handler<Throwable> handler);

  @Override
  NetSocket handler(Handler<Buffer> handler);

  @Override
  NetSocket pause();

  @Override
  NetSocket resume();

  @Override
  NetSocket endHandler(Handler<Void> endHandler);

  @Override
  NetSocket write(Buffer data);

  @Override
  NetSocket setWriteQueueMaxSize(int maxSize);

  @Override
  NetSocket drainHandler(Handler<Void> handler);

  /**
   * When a {@code NetSocket} is created it automatically registers an event handler with the event bus, the ID of that
   * handler is given by {@code writeHandlerID}.
   * <p>
   * Given this ID, a different event loop can send a buffer to that event handler using the event bus and
   * that buffer will be received by this instance in its own event loop and written to the underlying connection. This
   * allows you to write data to other connections which are owned by different event loops.
   *
   * @return the write handler ID
   */
  String writeHandlerID();

  /**
   * Write a {@link String} to the connection, encoded in UTF-8.
   *
   * @param str  the string to write
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetSocket write(String str);

  /**
   * Write a {@link String} to the connection, encoded using the encoding {@code enc}.
   *
   * @param str  the string to write
   * @param enc  the encoding to use
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetSocket write(String str, String enc);

  /**
   * Tell the operating system to stream a file as specified by {@code filename} directly from disk to the outgoing connection,
   * bypassing userspace altogether (where supported by the underlying operating system. This is a very efficient way to stream files.
   *
   * @param filename  file name of the file to send
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetSocket sendFile(String filename);

  /**
   * Same as {@link #sendFile(String)} but also takes a handler that will be called when the send has completed or
   * a failure has occurred
   *
   * @param filename  file name of the file to send
   * @param resultHandler  handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetSocket sendFile(String filename, Handler<AsyncResult<Void>> resultHandler);

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

  /**
   * Close the NetSocket
   */
  void close();

  /**
   * Set a handler that will be called when the NetSocket is closed
   *
   * @param handler  the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetSocket closeHandler(Handler<Void> handler);

  /**
   * Upgrade channel to use SSL/TLS. Be aware that for this to work SSL must be configured.
   *
   * @param handler  the handler will be notified when it's upgraded
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  NetSocket upgradeToSsl(Handler<Void> handler);

  /**
   * @return true if this {@link io.vertx.core.net.NetSocket} is encrypted via SSL/TLS.
   */
  boolean isSsl();
}

