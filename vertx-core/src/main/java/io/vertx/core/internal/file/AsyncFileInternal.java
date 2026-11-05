/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.file;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.internal.streams.WriteResult;
import io.vertx.core.internal.streams.WriteStreamInternal;

/**
 * Internal contract of {@link AsyncFile}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface AsyncFileInternal extends AsyncFile, WriteStreamInternal<Buffer> {

  @Override
  WriteResult write(Buffer data);

  @Override
  WriteResult write(Buffer buffer, long position);

  @Override
  AsyncFileInternal setWriteQueueMaxSize(int maxSize);

  @Override
  AsyncFileInternal drainHandler(Handler<Void> handler);

  @Override
  AsyncFileInternal exceptionHandler(Handler<Throwable> handler);

}
