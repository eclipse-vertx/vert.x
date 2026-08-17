/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.internal.eventbus;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.*;

public interface EventBusInternal extends EventBus {

  /**
   * Start the event bus.
   */
  void start(Promise<Void> promise);

  /**
   * Close the event bus and release any resources held.
   */
  void close(Promise<Void> promise);

  /**
   * Create a consumer and register it against the specified options address.
   *
   * @param context the consumer context
   * @param options  the consumer options
   *
   * @return the event bus message consumer
   */
  <T> MessageConsumer<T> consumer(Context context, MessageConsumerOptions options);

  /**
   * Like {@link #request(String, Object, DeliveryOptions)} but specifying {@code context} for the reply.
   *
   * @param context the reply context
   * @param address  the address to send it to
   * @param message  the message body, may be {@code null}
   * @param options  delivery options
   * @return a future notified when any reply from the recipient is received
   */
  <T> Future<Message<T>> request(Context context, String address, Object message, DeliveryOptions options);
}
