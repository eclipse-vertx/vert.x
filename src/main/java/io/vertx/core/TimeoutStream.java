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

package io.vertx.core;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.streams.ReadStream;

/**
 * A timeout stream is triggered by a timer, the {@link io.vertx.core.Handler} will be call when the timer is fired,
 * it can be once or several times depending on the nature of the timer related to this stream. The
 * {@link ReadStream#endHandler(Handler)} will be called after the timer handler has been called.
 * <p>
 * Pausing the timer inhibits the timer shots until the stream is resumed. Setting a null handler callback cancels
 * the timer.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface TimeoutStream extends ReadStream<Long> {

  @Override
  TimeoutStream exceptionHandler(Handler<Throwable> handler);

  @Override
  TimeoutStream handler(Handler<Long> handler);

  @Override
  TimeoutStream pause();

  @Override
  TimeoutStream resume();

  @Override
  TimeoutStream endHandler(Handler<Void> endHandler);

  /**
   * Cancels the timeout. Note this has the same effect as calling {@link #handler(Handler)} with a null
   * argument.
   */
  void cancel();

}
