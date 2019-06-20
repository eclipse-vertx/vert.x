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

package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;

/**
 * Represents a message that is received from the event bus in a handler.
 * <p>
 * Messages have a {@link #body}, which can be null, and also {@link #headers}, which can be empty.
 * <p>
 * If the message was sent specifying a reply handler, it can be replied to using {@link #reply}.
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
  @Nullable
  String replyAddress();

  /**
   * Signals if this message represents a send or publish event.
   *
   * @return true if this is a send.
   */
  boolean isSend();

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
   * Link {@link #reply(Object)} but allows you to specify delivery options for the reply.
   *
   * @param message  the reply message
   * @param options  the delivery options
   */
  void reply(Object message, DeliveryOptions options);

  /**
   * Reply to this message, specifying a {@code replyHandler} for the reply - i.e.
   * to receive the reply to the reply.
   * <p>
   * If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   *
   * @param message  the message to reply with.
   * @param replyHandler  the reply handler for the reply.
   */
  <R> void replyAndRequest(Object message, Handler<AsyncResult<Message<R>>> replyHandler);

  /**
   * Like {@link #replyAndRequest(Object, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default <R> Future<Message<R>> replyAndRequest(Object message) {
    Promise<Message<R>> promise = Promise.promise();
    replyAndRequest(message, promise);
    return promise.future();
  }

  /**
   * Like {@link #replyAndRequest(Object, Handler)} but specifying {@code options} that can be used
   * to configure the delivery.
   *
   * @param message  the message body, may be {@code null}
   * @param options  delivery options
   * @param replyHandler  reply handler will be called when any reply from the recipient is received
   */
  <R> void replyAndRequest(Object message, DeliveryOptions options, Handler<AsyncResult<Message<R>>> replyHandler);

  /**
   * Like {@link #replyAndRequest(Object, DeliveryOptions, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default <R> Future<Message<R>> replyAndRequest(Object message, DeliveryOptions options) {
    Promise<Message<R>> promise = Promise.promise();
    replyAndRequest(message, options, promise);
    return promise.future();
  }

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
