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

package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;

/**
 * An event bus consumer object representing a stream of message to an {@link EventBus} address that can
 * be read from.
 * <p>
 * The {@link EventBus#consumer(String)} or {@link EventBus#localConsumer(String)}
 * creates a new consumer, the returned consumer is not yet registered against the event bus. Registration
 * is effective after the {@link #handler(io.vertx.core.Handler)} method is invoked.<p>
 *
 * The consumer is unregistered from the event bus using the {@link #unregister()} method or by calling the
 * {@link #handler(io.vertx.core.Handler)} with a null value..
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
@VertxGen
public interface MessageConsumer<T> extends ReadStream<Message<T>> {

  @Override
  MessageConsumer<T> exceptionHandler(Handler<Throwable> handler);

  @Override
  MessageConsumer<T> handler(Handler<Message<T>> handler);

  @Override
  MessageConsumer<T> pause();

  @Override
  MessageConsumer<T> resume();

  @Override
  MessageConsumer<T> fetch(long amount);

  @Override
  MessageConsumer<T> endHandler(Handler<Void> endHandler);

  /**
   * @return a read stream for the body of the message stream.
   */
  ReadStream<T> bodyStream();

  /**
   * @return true if the current consumer is registered
   */
  boolean isRegistered();

  /**
   * @return The address the handler was registered with.
   */
  String address();

  /**
   * @return a future notified when the message consumer is registered
   */
  Future<Void> completion();

  /**
   * Unregisters the handler which created this registration
   *
   * @return a future notified when the unregistation is done
   */
  Future<Void> unregister();

}
