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

package io.vertx.core.net;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
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
 * {@link io.vertx.core.streams.Pipe} to pipe data with flow control.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface NetSocket extends TcpSocket {

  @Override
  NetSocket exceptionHandler(Handler<Throwable> handler);

  @Override
  NetSocket handler(Handler<Buffer> handler);

  @Override
  NetSocket pause();

  @Override
  NetSocket resume();

  @Override
  NetSocket fetch(long amount);

  /**
   * {@inheritDoc}
   * <p>
   * This handler might be called after the close handler when the socket is paused and there are still
   * buffers to deliver.
   */
  @Override
  NetSocket endHandler(Handler<Void> endHandler);

  @Override
  NetSocket setWriteQueueMaxSize(int maxSize);

  @Override
  NetSocket drainHandler(Handler<Void> handler);

  /**
   * When a {@code NetSocket} is created, it may register an event handler with the event bus, the ID of that
   * handler is given by {@code writeHandlerID}.
   * <p>
   * By default, no handler is registered, the feature must be enabled via {@link NetClientOptions#setRegisterWriteHandler(boolean)} or {@link NetServerOptions#setRegisterWriteHandler(boolean)}.
   * <p>
   * Given this ID, a different event loop can send a buffer to that event handler using the event bus and
   * that buffer will be received by this instance in its own event loop and written to the underlying connection. This
   * allows you to write data to other connections which are owned by different event loops.
   *
   * @return the write handler ID
   * @see NetClientOptions#setRegisterWriteHandler(boolean)
   * @see NetServerOptions#setRegisterWriteHandler(boolean)
   */
  String writeHandlerID();

}

