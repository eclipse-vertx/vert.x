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

package io.vertx.core.streams;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;

/**
 * Represents a stream of data that can be read from.<p>
 * Any class that implements this interface can be used by a {@link Pump} to pump data from it
 * to a {@link WriteStream}.<p>
 * This interface exposes a fluent api and the type T represents the type of the object that implements
 * the interface to allow method chaining
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen(concrete = false)
public interface ReadStream<T> {

  /**
   * Set a data handler. As data is read, the handler will be called with the data.
   */
  @Fluent
  T dataHandler(Handler<Buffer> handler);

  /**
   * Pause the {@code ReadSupport}. While it's paused, no data will be sent to the {@code dataHandler}
   */
  @Fluent
  T pause();

  /**
   * Resume reading. If the {@code ReadSupport} has been paused, reading will recommence on it.
   */
  @Fluent
  T resume();

  /**
   * Set an end handler. Once the stream has ended, and there is no more data to be read, this handler will be called.
   */
  @Fluent
  T endHandler(Handler<Void> endHandler);

  /**
   * Set an exception handler.
   */
  @Fluent
  T exceptionHandler(Handler<Throwable> handler);
}
