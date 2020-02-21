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

import java.util.List;

/**
 * A stream of the state of {@link io.vertx.core.eventbus.EventBus} registrations for a given address.
 * <p>
 * Invoke {@link #stop()} to stop listening before the end of the stream.
 * Otherwise the implementation (specific to each cluster manager) could leak resources.
 * <p>
 * Beware that handlers for this stream can be invoked on any thread (specific to each cluster manager).
 *
 * @author Thomas Segismont
 */
public interface RegistrationStream {

  /**
   * Return the initial state of registrations for a given address.
   * <p>
   * The result may no longer be available after {@link #start()} has been invoked.
   */
  List<RegistrationInfo> initialState();

  /**
   * Set the handler to be called when the list of registrations changes.
   * <p>
   * The handler is invoked only when the list is not empty.
   */
  RegistrationStream handler(Handler<List<RegistrationInfo>> handler);

  /**
   * Set the handler to be called when the stream is broken and should no longer be used.
   * <p>
   * It is not necessary to invoked {@link #stop()} afterwards.
   */
  RegistrationStream exceptionHandler(Handler<Throwable> handler);

  /**
   * Set the handler to be called when there are no listeners anymore.
   * <p>
   * It is not necessary to invoked {@link #stop()} afterwards.
   */
  RegistrationStream endHandler(Handler<Void> endHandler);

  /**
   * Start listening.
   */
  void start();

  /**
   * Stop listening.
   */
  void stop();
}
