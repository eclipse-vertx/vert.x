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

package io.vertx.core.http.impl.pool;

import io.netty.channel.Channel;
import io.vertx.core.impl.ContextImpl;

/**
 * The listener definest the contract used by the {@link ConnectionProvider} to interact with the
 * connection pool. Its purpose is also to use a connection implementation without a pool.
 */
public interface ConnectionListener<C> {

  /**
   * Callback to signal the connection succeeded and provide all the info requires to manage the connection
   *
   * @param conn the connection
   * @param concurrency the connection concurrency
   * @param channel the channel
   * @param context the context
   * @param initialWeight the initial weight
   * @param actualWeight the actual weight
   */
  void onConnectSuccess(C conn,
                        long concurrency,
                        Channel channel,
                        ContextImpl context,
                        long initialWeight,
                        long actualWeight);

  /**
   * Callback to signal the connection failed.
   *
   * @param context the context
   * @param err the error
   * @param weight the weight
   */
  void onConnectFailure(ContextImpl context, Throwable err, long weight);

  /**
   * Signals the connrection changed to the {@code concurrency} value.
   *
   * @param concurrency the concurrency
   */
  void onConcurrencyChange(long concurrency);

  /**
   * Signals the connection can recycled, it must not give back more than it borrowed.
   *
   * @param capacity the capacity to recycle
   * @param disposable wether the connection can be disposed
   */
  void onRecycle(int capacity, boolean disposable);

  /**
   * Signals the connection must not be used anymore by the pool.
   */
  void onDiscard();

}
