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

package io.vertx.core.streams;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;

@VertxGen(concrete = false)
public interface DuplexStream<T> extends ReadStream<T>, WriteStream<T> {


  DuplexStream<T> exceptionHandler(Handler<Throwable> handler);
  DuplexStream<T> handler(@Nullable Handler<T> handler);
  DuplexStream<T> pause();
  DuplexStream<T> resume();
  DuplexStream<T> fetch(long amount);
  DuplexStream<T> endHandler(@Nullable Handler<Void> endHandler);
  DuplexStream<T> setWriteQueueMaxSize(int maxSize);
  DuplexStream<T> drainHandler(@Nullable Handler<Void> handler);
}
