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

package io.vertx.core.spi.cluster;

import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;

import java.util.List;

/**
 * A stream of  the state of {@link io.vertx.core.eventbus.EventBus} registrations for a given address.
 * <p>
 * Always invoke {@link #close()} eventually to avoid leaking resources.
 *
 * @author Thomas Segismont
 */
public interface RegistrationStream extends ReadStream<List<RegistrationInfo>> {

  /**
   * Return the {@link io.vertx.core.eventbus.EventBus} address related to this stream.
   */
  String address();

  /**
   * Return the initial state of registrations for a given address.
   * <p>
   * The result may no longer be available after a {@link #handler(Handler)} has been set.
   */
  List<RegistrationInfo> initialState();

  @Override
  RegistrationStream exceptionHandler(Handler<Throwable> handler);

  @Override
  RegistrationStream handler(Handler<List<RegistrationInfo>> handler);

  @Override
  RegistrationStream pause();

  @Override
  RegistrationStream resume();

  @Override
  RegistrationStream fetch(long amount);

  @Override
  RegistrationStream endHandler(Handler<Void> endHandler);

  /**
   * Close the stream.
   * <p>
   * Note this has the same effect as calling {@link #handler(Handler)} with a {@code null} value.
   * argument.
   */
  void close();
}
