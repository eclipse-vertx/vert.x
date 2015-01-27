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
 * Represents a message that is received from the event bus in a handler.
 * <p>
 * Messages have a {@link #body}, which can be null, and also {@link #headers}, which can be empty.
 * <p>
 * If the message was sent specifying a reply handler it will also have a {@link #replyAddress}. In that case the message
 * can be replied to using that reply address, or, more simply by just using {@link #reply}.
 * <p>
 * If you want to notify the sender that processing failed, then {@link #fail} can be called.
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
   * Multi-map of message headers. Can be empty
   *
   * @return  the headers
   */
  MultiMap headers();

  /**
   * The body of the message. Can be null.
   *
   * @return  the body, or null.
   */
  @CacheReturn
  T body();

  /**
   * The reply address. Can be null.
   *
   * @return the reply address, or null, if message was sent without a reply handler.
   */
  String replyAddress();

  /**
   * Reply to this message.
   * <p>
   * If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   *
   * @param message  the message to reply with.
   */
  void reply(Object message);

  /**
   * The same as {@code reply(R message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   *
   * @param message  the message to reply with.
   * @param replyHandler  the reply handler for the reply.
   */
  <R> void reply(Object message, Handler<AsyncResult<Message<R>>> replyHandler);

  /**
   * Link {@link #reply(Object)} but allows you to specify delivery options for the reply.
   *
   * @param message  the reply message
   * @param options  the delivery options
   */
  void reply(Object message, DeliveryOptions options);

  /**
   * The same as {@code reply(R message, DeliveryOptions)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   *
   * @param message  the reply message
   * @param options  the delivery options
   * @param replyHandler  the reply handler for the reply.
   */
  <R> void reply(Object message, DeliveryOptions options, Handler<AsyncResult<Message<R>>> replyHandler);

  /**
   * Signal to the sender that processing of this message failed.
   * <p>
   * If the message was sent specifying a result handler
   * the handler will be called with a failure corresponding to the failure code and message specified here.
   *
   * @param failureCode A failure code to pass back to the sender
   * @param message A message to pass back to the sender
   */
  void fail(int failureCode, String message);

}
