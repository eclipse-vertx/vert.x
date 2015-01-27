/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.streams.WriteStream;

/**
 * Represents a stream of message that can be written to.
 * <p>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface MessageProducer<T> extends WriteStream<T> {

  @Override
  MessageProducer<T> exceptionHandler(Handler<Throwable> handler);

  @Override
  MessageProducer<T> write(T data);

  @Override
  MessageProducer<T> setWriteQueueMaxSize(int maxSize);

  @Override
  MessageProducer<T> drainHandler(Handler<Void> handler);

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

}
