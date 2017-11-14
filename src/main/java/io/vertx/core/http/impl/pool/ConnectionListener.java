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
   * Signals the connection is recycled, this should not be called more than the connection has been borrowed.
   */
  void onRecycle();

  /**
   * Signals the connection is closed.
   */
  void onClose();

}
