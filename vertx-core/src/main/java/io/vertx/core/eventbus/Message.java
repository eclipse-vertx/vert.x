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

package io.vertx.core.eventbus;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.gen.VertxGen;

/**
 * Represents a message on the event bus.<p>
 *
 * Instances of this class are not thread-safe<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface Message<T> {

  /**
   * The address the message was sent to
   */
  String address();

  /**
   * The body of the message
   */
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
  <R> void reply(Object message, Handler<Message<R>> replyHandler);

  /**
   * Reply to this message. Specifying a timeout
   */
  <R> void replyWithTimeout(Object message, long timeout);

  /**
   * Reply to this message. Specifying a timeout and a reply handler
   */
  <R> void replyWithTimeout(Object message, long timeout, Handler<AsyncResult<Message<R>>> replyHandler);

  /**
   * Signal that processing of this message failed. If the message was sent specifying a result handler
   * the handler will be called with a failure corresponding to the failure code and message specified here
   * @param failureCode A failure code to pass back to the sender
   * @param message A message to pass back to the sender
   */
  void fail(int failureCode, String message);

}
