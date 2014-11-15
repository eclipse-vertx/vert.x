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
      delegate.handler(message -> {
        handler.handle(message.body());
      });
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
