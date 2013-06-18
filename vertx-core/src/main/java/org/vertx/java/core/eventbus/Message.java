/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.eventbus;

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
   * The same as {@code reply(JsonObject message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Object message, Handler<Message<T>> replyHandler);

  /**
   * The same as {@code reply(JsonObject message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(JsonObject message, Handler<Message<T>> replyHandler);

  /**
   * The same as {@code reply(JsonArray message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(JsonArray message, Handler<Message<T>> replyHandler);

  /**
   * The same as {@code reply(String message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(String message, Handler<Message<T>> replyHandler);

  /**
   * The same as {@code reply(Buffer message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Buffer message, Handler<Message<T>> replyHandler);

  /**
   * The same as {@code reply(byte[] message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(byte[] message, Handler<Message<T>> replyHandler);

  /**
   * The same as {@code reply(Integer message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Integer message, Handler<Message<T>> replyHandler);

  /**
   * The same as {@code reply(Long message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Long message, Handler<Message<T>> replyHandler);

  /**
   * The same as {@code reply(Short message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Short message, Handler<Message<T>> replyHandler);

  /**
   * The same as {@code reply(Character message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Character message, Handler<Message<T>> replyHandler);

  /**
   * The same as {@code reply(Boolean message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Boolean message, Handler<Message<T>> replyHandler);

  /**
   * The same as {@code reply(Float message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Float message, Handler<Message<T>> replyHandler);

  /**
   * The same as {@code reply(Double message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  <T> void reply(Double message, Handler<Message<T>> replyHandler);

}
