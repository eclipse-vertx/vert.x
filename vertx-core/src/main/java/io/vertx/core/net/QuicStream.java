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
package io.vertx.core.net;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

/**
 * A Quic stream.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface QuicStream extends Socket {

  long id();

  /**
   * @return whether the stream is unidirectional or bidirectional
   */
  boolean isBidirectional();

  /**
   * @return whether the stream was created by this connection
   */
  boolean isLocalCreated();

  /**
   * @return the Quic connection this streams belongs to
   */
  QuicConnection connection();

  /**
   * Set a handler called upon stream reset.
   *
   * @param handler the handler
   * @return this instance of a stream
   */
  QuicStream resetHandler(@Nullable Handler<Integer> handler);

  /**
   * Send a stream reset frame to the remote stream.
   *
   * @param error the error code
   * @return a future completed when the reset frame has been sent
   */
  Future<Void> reset(int error);

  /**
   * Set a handler called when the stream is closed.
   *
   * @param handler the handler signaled with the stream close
   * @return this instance of a stream
   */
  @Override
  QuicStream closeHandler(@Nullable Handler<Void> handler);

  /**
   * Set a handler called when the stream is shutdown.
   *
   * @param handler the handler signaled with the stream shutdown
   * @return this instance of a stream
   */
  @Override
  QuicStream shutdownHandler(@Nullable Handler<Void> handler);

  /**
   * Set an exception handling, catching stream exceptions.
   *
   * @param handler  the exception handler
   * @return this instance of a stream
   */
  @Override
  QuicStream exceptionHandler(@Nullable Handler<Throwable> handler);

  /**
   * Set the handler signaled with the data events the remote peer has sent.
   *
   * @param handler the data event handler
   * @return this instance of a stream
   */
  @Override
  QuicStream handler(@Nullable Handler<Buffer> handler);

  @Override
  QuicStream pause();

  @Override
  QuicStream resume();

  @Override
  QuicStream fetch(long amount);

  @Override
  QuicStream endHandler(@Nullable Handler<Void> endHandler);

  @Override
  QuicStream setWriteQueueMaxSize(int maxSize);

  @Override
  QuicStream drainHandler(@Nullable Handler<Void> handler);
}
