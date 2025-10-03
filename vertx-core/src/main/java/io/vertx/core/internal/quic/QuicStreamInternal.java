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
package io.vertx.core.internal.quic;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.net.SocketInternal;
import io.vertx.core.net.QuicStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface QuicStreamInternal extends QuicStream, SocketInternal {

  @Override
  QuicStreamInternal messageHandler(Handler<Object> handler);

  @Override
  QuicStreamInternal readCompletionHandler(Handler<Void> handler);

  @Override
  QuicStreamInternal eventHandler(Handler<Object> handler);

  @Override
  QuicStreamInternal exceptionHandler(@Nullable Handler<Throwable> handler);

  @Override
  QuicStreamInternal handler(@Nullable Handler<Buffer> handler);

  @Override
  QuicStreamInternal pause();

  @Override
  QuicStreamInternal resume();

  @Override
  QuicStreamInternal fetch(long amount);

  @Override
  QuicStreamInternal endHandler(@Nullable Handler<Void> endHandler);

  @Override
  QuicStreamInternal setWriteQueueMaxSize(int maxSize);

  @Override
  QuicStreamInternal drainHandler(@Nullable Handler<Void> handler);

  @Override
  QuicStreamInternal closeHandler(@Nullable Handler<Void> handler);

}
