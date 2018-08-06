/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.eventbus.impl;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.streams.ReadStream;

/**
 * A body stream that transform a <code>ReadStream&lt;Message&lt;T&gt;&gt;</code> into a
 * <code>ReadStream&lt;T&gt;</code>.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class BodyReadStream<T> implements ReadStream<T> {

  private ReadStream<Message<T>> delegate;

  public BodyReadStream(ReadStream<Message<T>> delegate) {
    this.delegate = delegate;
  }

  @Override
  public ReadStream<T> exceptionHandler(Handler<Throwable> handler) {
    delegate.exceptionHandler(handler);
    return null;
  }

  @Override
  public ReadStream<T> handler(Handler<T> handler) {
    if (handler != null) {
      delegate.handler(message -> handler.handle(message.body()));
    } else {
      delegate.handler(null);
    }
    return this;
  }

  @Override
  public ReadStream<T> pause() {
    delegate.pause();
    return this;
  }

  @Override
  public ReadStream<T> fetch(long amount) {
    delegate.fetch(amount);
    return this;
  }

  @Override
  public ReadStream<T> resume() {
    delegate.resume();
    return this;
  }

  @Override
  public ReadStream<T> endHandler(Handler<Void> endHandler) {
    delegate.endHandler(endHandler);
    return this;
  }
}
