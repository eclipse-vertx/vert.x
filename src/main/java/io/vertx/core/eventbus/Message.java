/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface Message<T> {

  /**
   * The address the message was sent to
   */
  String address();

  MultiMap headers();

  /**
   * The body of the message
   */
  @CacheReturn
  T body();

  /**
   * The reply address (if any)
   */
  String replyAddress();

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  void reply(Object message);

  /**
   * The same as {@code reply(R message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <R> void reply(Object message, Handler<AsyncResult<Message<R>>> replyHandler);

  void reply(Object message, DeliveryOptions options);

  /**
   * The same as {@code reply(R message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <R> void reply(Object message, DeliveryOptions options, Handler<AsyncResult<Message<R>>> replyHandler);

  /**
   * Signal that processing of this message failed. If the message was sent specifying a result handler
   * the handler will be called with a failure corresponding to the failure code and message specified here
   * @param failureCode A failure code to pass back to the sender
   * @param message A message to pass back to the sender
   */
  void fail(int failureCode, String message);

}
