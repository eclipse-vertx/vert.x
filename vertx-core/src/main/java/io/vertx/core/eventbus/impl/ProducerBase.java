/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.eventbus.impl;

import io.vertx.core.Handler;
import io.vertx.core.streams.WriteStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@FunctionalInterface
public interface ProducerBase<T> extends WriteStream<T>, Handler<T> {

  @Override
  public default WriteStream<T> exceptionHandler(Handler<Throwable> handler) {
    return this;
  }

  @Override
  public default WriteStream<T> setWriteQueueMaxSize(int maxSize) {
    return this;
  }

  @Override
  public default WriteStream<T> write(T data) {
    handle(data);
    return this;
  }

  @Override
  public default boolean writeQueueFull() {
    return false;
  }

  @Override
  public default WriteStream<T> drainHandler(Handler<Void> handler) {
    return this;
  }

}
