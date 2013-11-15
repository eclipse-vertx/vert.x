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

package org.vertx.java.core.eventbus;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

/**
 * Represents a message on the event bus.<p>
 *
 * Instances of this class are not thread-safe<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
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
   * Same as {@code reply(T message)} but with an empty body
   */
  void reply();

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  void reply(Object message);

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  void reply(JsonObject message);

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  void reply(JsonArray message);

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  void reply(String message);

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  void reply(Buffer message);

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  void reply(byte[] message);

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  void reply(Integer message);

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  void reply(Long message);

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  void reply(Short message);

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  void reply(Character message);

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  void reply(Boolean message);

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  void reply(Float message);

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  void reply(Double message);

  /**
   * The same as {@code reply()} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Handler<Message<T>> replyHandler);

  /**
   * Reply to this message. Specifying a timeout and a reply handler
   */
  <T> void replyWithTimeout(long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * The same as {@code reply(JsonObject message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Object message, Handler<Message<T>> replyHandler);

  /**
   * Reply to this message. Specifying a timeout and a reply handler
   */
  <T> void replyWithTimeout(Object message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * The same as {@code reply(JsonObject message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(JsonObject message, Handler<Message<T>> replyHandler);

  /**
   * Reply to this message. Specifying a timeout and a reply handler
   */
  <T> void replyWithTimeout(JsonObject message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * The same as {@code reply(JsonArray message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(JsonArray message, Handler<Message<T>> replyHandler);

  /**
   * Reply to this message. Specifying a timeout and a reply handler
   */
  <T> void replyWithTimeout(JsonArray message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * The same as {@code reply(String message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(String message, Handler<Message<T>> replyHandler);

  /**
   * Reply to this message. Specifying a timeout and a reply handler
   */
  <T> void replyWithTimeout(String message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * The same as {@code reply(Buffer message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Buffer message, Handler<Message<T>> replyHandler);

  /**
   * Reply to this message. Specifying a timeout and a reply handler
   */
  <T> void replyWithTimeout(Buffer message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * The same as {@code reply(byte[] message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(byte[] message, Handler<Message<T>> replyHandler);

  /**
   * Reply to this message. Specifying a timeout and a reply handler
   */
  <T> void replyWithTimeout(byte[] message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * The same as {@code reply(Integer message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Integer message, Handler<Message<T>> replyHandler);

  /**
   * Reply to this message. Specifying a timeout and a reply handler
   */
  <T> void replyWithTimeout(Integer message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * The same as {@code reply(Long message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Long message, Handler<Message<T>> replyHandler);

  /**
   * Reply to this message. Specifying a timeout and a reply handler
   */
  <T> void replyWithTimeout(Long message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * The same as {@code reply(Short message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Short message, Handler<Message<T>> replyHandler);

  /**
   * Reply to this message. Specifying a timeout and a reply handler
   */
  <T> void replyWithTimeout(Short message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * The same as {@code reply(Character message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Character message, Handler<Message<T>> replyHandler);

  /**
   * Reply to this message. Specifying a timeout and a reply handler
   */
  <T> void replyWithTimeout(Character message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * The same as {@code reply(Boolean message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Boolean message, Handler<Message<T>> replyHandler);

  /**
   * Reply to this message. Specifying a timeout and a reply handler
   */
  <T> void replyWithTimeout(Boolean message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * The same as {@code reply(Float message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Float message, Handler<Message<T>> replyHandler);

  /**
   * Reply to this message. Specifying a timeout and a reply handler
   */
  <T> void replyWithTimeout(Float message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * The same as {@code reply(Double message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Double message, Handler<Message<T>> replyHandler);

  /**
   * Reply to this message. Specifying a timeout and a reply handler
   */
  <T> void replyWithTimeout(Double message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * Signal that processing of this message failed. If the message was sent specifying a result handler
   * the handler will be called with a failure corresponding to the failure code and message specified here
   * @param failureCode A failure code to pass back to the sender
   * @param message A message to pass back to the sender
   */
  void fail(int failureCode, String message);

}
