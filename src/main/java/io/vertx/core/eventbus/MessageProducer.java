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

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * Represents a stream of message that can be written to.
 * <p>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface MessageProducer<T> {

  /**
   * Update the delivery options of this producer.
   *
   * @param options the new options
   * @return this producer object
   */
  @Fluent
  MessageProducer<T> deliveryOptions(DeliveryOptions options);

  /**
   * @return The address to which the producer produces messages.
   */
  String address();

  /**
   * Write a message to the event-bus, either sending or publishing.
   *
   * The {@code handler} completion depends on the producer type:
   *
   * <ul>
   *   <li>send or request: the handler is completed successfully if the message has been written; otherwise, the handler is failed</li>
   *   <li>publish: the handler is failed if there is no recipient; otherwise, the handler is completed successfully</li>
   * </ul>
   *
   * In any case, a successfully completed {@code handler} is not a delivery guarantee.
   *
   * @param body the message body
   */
  void write(T body, Handler<AsyncResult<Void>> handler);

  /**
   * Like {@link #write(Object, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Void> write(T body);

  /**
   * Closes the producer, this method should be called when the message producer is not used anymore.
   *
   * @return a future completed with the result
   */
  Future<Void> close();

  /**
   * Same as {@link #close()} but with an {@code handler} called when the operation completes
   */
  void close(Handler<AsyncResult<Void>> handler);
}
