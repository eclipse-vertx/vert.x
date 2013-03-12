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
import org.vertx.java.core.eventbus.impl.BaseMessage;
import org.vertx.java.core.eventbus.impl.JsonObjectMessage;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

/**
 * Represents a message on the event bus.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class Message<T>  {

  protected Message() {
  }

  /**
   * The body of the message
   */
  public T body;

  /**
   * The reply address (if any)
   */
  public String replyAddress;

  /**
   * Same as {@code reply(T message)} but with an empty body
   */
  public void reply() {
    reply((String)null);
  }

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  public void reply(Object message) {
    reply(message, null);
  }

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  public void reply(JsonObject message) {
    reply(message, null);
  }

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  public void reply(JsonArray message) {
    reply(message, null);
  }

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  public void reply(String message) {
    reply(message, null);
  }

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  public void reply(Buffer message) {
    reply(message, null);
  }

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  public void reply(byte[] message) {
    reply(message, null);
  }

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  public void reply(Integer message) {
    reply(message, null);
  }

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  public void reply(Long message) {
    reply(message, null);
  }

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  public void reply(Short message) {
    reply(message, null);
  }

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  public void reply(Character message) {
    reply(message, null);
  }

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  public void reply(Boolean message) {
    reply(message, null);
  }

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  public void reply(Float message) {
    reply(message, null);
  }

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  public void reply(Double message) {
    reply(message, null);
  }

  /**
   * The same as {@code reply(JsonObject message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  public void reply(Object message, Handler<Message> replyHandler) {
    doReply(message, replyHandler);
  }

  /**
   * The same as {@code reply(JsonObject message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  public void reply(JsonObject message, Handler<Message> replyHandler) {
    doReply(message, replyHandler);
  }

  /**
   * The same as {@code reply(JsonArray message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  public void reply(JsonArray message, Handler<Message> replyHandler) {
    doReply(message, null);
  }

  /**
   * The same as {@code reply(String message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  public void reply(String message, Handler<Message> replyHandler) {
    doReply(message, null);
  }

  /**
   * The same as {@code reply(Buffer message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  public void reply(Buffer message, Handler<Message> replyHandler) {
    doReply(message, null);
  }

  /**
   * The same as {@code reply(byte[] message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  public void reply(byte[] message, Handler<Message> replyHandler) {
    doReply(message, null);
  }

  /**
   * The same as {@code reply(Integer message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  public void reply(Integer message, Handler<Message> replyHandler) {
    doReply(message, null);
  }

  /**
   * The same as {@code reply(Long message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  public void reply(Long message, Handler<Message> replyHandler) {
    doReply(message, null);
  }

  /**
   * The same as {@code reply(Short message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  public void reply(Short message, Handler<Message> replyHandler) {
    doReply(message, null);
  }

  /**
   * The same as {@code reply(Character message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  public void reply(Character message, Handler<Message> replyHandler) {
    doReply(message, null);
  }

  /**
   * The same as {@code reply(Boolean message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  public void reply(Boolean message, Handler<Message> replyHandler) {
    doReply(message, null);
  }

  /**
   * The same as {@code reply(Float message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  public void reply(Float message, Handler<Message> replyHandler) {
    doReply(message, null);
  }

  /**
   * The same as {@code reply(Double message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  public void reply(Double message, Handler<Message> replyHandler) {
    doReply(message, null);
  }

  protected abstract void doReply(Object message, Handler<Message> replyHandler);

  protected abstract void doReply(JsonObject message, Handler<Message> replyHandler);

  protected abstract void doReply(JsonArray message, Handler<Message> replyHandler);

  protected abstract void doReply(String message, Handler<Message> replyHandler);

  protected abstract void doReply(Buffer message, Handler<Message> replyHandler);

  protected abstract void doReply(byte[] message, Handler<Message> replyHandler);

  protected abstract void doReply(Integer message, Handler<Message> replyHandler);

  protected abstract void doReply(Long message, Handler<Message> replyHandler);

  protected abstract void doReply(Short message, Handler<Message> replyHandler);

  protected abstract void doReply(Character message, Handler<Message> replyHandler);

  protected abstract void doReply(Boolean message, Handler<Message> replyHandler);

  protected abstract void doReply(Float message, Handler<Message> replyHandler);

  protected abstract void doReply(Double message, Handler<Message> replyHandler);

}
