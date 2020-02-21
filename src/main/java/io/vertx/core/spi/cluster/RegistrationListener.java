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
 * A listener to the state of {@link io.vertx.core.eventbus.EventBus} registrations for a given address.
 * <p>
 * Invoke {@link #stop()} to stop listening, otherwise the implementation may leak resources.
 * <p>
 * Threading notes:
 * <ul>
 *   <li>handlers can be invoked on any thread</li>
 *   <li>handlers may block the callback thread</li>
 *   <li>handlers must not be invoked concurrently</li>
 * </ul>
 *
 * @author Thomas Segismont
 */
public interface RegistrationListener {

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
  RegistrationListener handler(Handler<List<RegistrationInfo>> handler);

  /**
   * Set the handler to be called when the listener is broken and should no longer be used.
   * <p>
   * It is not necessary to invoked {@link #stop()} afterwards.
   */
  RegistrationListener exceptionHandler(Handler<Throwable> handler);

  /**
   * Set the handler to be called when there aren't any more registrations.
   * <p>
   * It is not necessary to invoked {@link #stop()} afterwards.
   */
  RegistrationListener endHandler(Handler<Void> endHandler);

  /**
   * Start listening.
   */
  void start();

  /**
   * Stop listening.
   */
  void stop();
}
