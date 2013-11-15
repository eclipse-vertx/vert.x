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
 * A distributed lightweight event bus which can encompass multiple vert.x instances.
 * The event bus implements publish / subscribe, point to point messaging and request-response messaging.<p>
 * Messages sent over the event bus are represented by instances of the {@link Message} class.<p>
 * For publish / subscribe, messages can be published to an address using one of the {@link #publish} methods. An
 * address is a simple {@code String} instance.<p>
 * Handlers are registered against an address. There can be multiple handlers registered against each address, and a particular handler can
 * be registered against multiple addresses. The event bus will route a sent message to all handlers which are
 * registered against that address.<p>
 * For point to point messaging, messages can be sent to an address using one of the {@link #send} methods.
 * The messages will be delivered to a single handler, if one is registered on that address. If more than one
 * handler is registered on the same address, Vert.x will choose one and deliver the message to that. Vert.x will
 * aim to fairly distribute messages in a round-robin way, but does not guarantee strict round-robin under all
 * circumstances.<p>
 * All messages sent over the bus are transient. On event of failure of all or part of the event bus messages
 * may be lost. Applications should be coded to cope with lost messages, e.g. by resending them, and making application
 * services idempotent.<p>
 * The order of messages received by any specific handler from a specific sender should match the order of messages
 * sent from that sender.<p>
 * When sending a message, a reply handler can be provided. If so, it will be called when the reply from the receiver
 * has been received. Reply messages can also be replied to, etc, ad infinitum<p>
 * Different event bus instances can be clustered together over a network, to give a single logical event bus.<p>
 * Instances of EventBus are thread-safe.<p>
 * If handlers are registered from an event loop, they will be executed using that same event loop. If they are
 * registered from outside an event loop (i.e. when using Vert.x embedded) then Vert.x will assign an event loop
 * to the handler and use it to deliver messages to that handler.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface EventBus {

	/**
	 * Close the EventBus and release all resources. 
	 * 
	 * @param doneHandler
	 */
  void close(Handler<AsyncResult<Void>> doneHandler);

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   */
  EventBus send(String address, Object message);

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  EventBus send(String address, Object message, Handler<Message> replyHandler);

  /**
   * Send an object as a message
   * @param address The address to send it to
   * @param message The message
   * @param timeout - Timeout in ms. If no reply received within the timeout then the reply handler will be unregistered
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus sendWithTimeout(String address, Object message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * Send a JSON object as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus send(String address, JsonObject message, Handler<Message<T>> replyHandler);

  /**
   * Send a JSON object as a message
   * @param address The address to send it to
   * @param message The message
   * @param timeout - Timeout in ms. If no reply received within the timeout then the reply handler will be unregistered
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus sendWithTimeout(String address, JsonObject message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * Send a JSON object as a message
   * @param address The address to send it to
   * @param message The message
   */
  EventBus send(String address, JsonObject message);

  /**
   * Send a JSON array as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus send(String address, JsonArray message, Handler<Message<T>> replyHandler);

  /**
   * Send a JSON array as a message
   * @param address The address to send it to
   * @param message The message
   * @param timeout - Timeout in ms. If no reply received within the timeout then the reply handler will be unregistered
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus sendWithTimeout(String address, JsonArray message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);


  /**
   * Send a JSON array as a message
   * @param address The address to send it to
   * @param message The message
   */
  EventBus send(String address, JsonArray message);

  /**
   * Send a Buffer as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus send(String address, Buffer message, Handler<Message<T>> replyHandler);

  /**
   * Send a Buffer object as a message
   * @param address The address to send it to
   * @param message The message
   * @param timeout - Timeout in ms. If no reply received within the timeout then the reply handler will be unregistered
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus sendWithTimeout(String address, Buffer message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * Send a Buffer as a message
   * @param address The address to send it to
   * @param message The message
   */
  EventBus send(String address, Buffer message);

  /**
   * Send a byte[] as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus send(String address, byte[] message, Handler<Message<T>> replyHandler);

  /**
   * Send a byte[] object as a message
   * @param address The address to send it to
   * @param message The message
   * @param timeout - Timeout in ms. If no reply received within the timeout then the reply handler will be unregistered
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus sendWithTimeout(String address, byte[] message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);


  /**
   * Send a byte[] as a message
   * @param address The address to send it to
   * @param message The message
   */
  EventBus send(String address, byte[] message);

  /**
   * Send a String as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus send(String address, String message, Handler<Message<T>> replyHandler);

  /**
   * Send a string object as a message
   * @param address The address to send it to
   * @param message The message
   * @param timeout - Timeout in ms. If no reply received within the timeout then the reply handler will be unregistered
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus sendWithTimeout(String address, String message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * Send a String as a message
   * @param address The address to send it to
   * @param message The message
   */
  EventBus send(String address, String message);

  /**
   * Send an Integer as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus send(String address, Integer message, Handler<Message<T>> replyHandler);

  /**
   * Send an Integer as a message
   * @param address The address to send it to
   * @param message The message
   * @param timeout - Timeout in ms. If no reply received within the timeout then the reply handler will be unregistered
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus sendWithTimeout(String address, Integer message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * Send an Integer as a message
   * @param address The address to send it to
   * @param message The message
   */
  EventBus send(String address, Integer message);

  /**
   * Send a Long as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus send(String address, Long message, Handler<Message<T>> replyHandler);

  /**
   * Send a long as a message
   * @param address The address to send it to
   * @param message The message
   * @param timeout - Timeout in ms. If no reply received within the timeout then the reply handler will be unregistered
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus sendWithTimeout(String address, Long message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * Send a Long as a message
   * @param address The address to send it to
   * @param message The message
   */
  EventBus send(String address, Long message);

  /**
   * Send a Float as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus send(String address, Float message, Handler<Message<T>> replyHandler);

  /**
   * Send a float as a message
   * @param address The address to send it to
   * @param message The message
   * @param timeout - Timeout in ms. If no reply received within the timeout then the reply handler will be unregistered
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus sendWithTimeout(String address, Float message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * Send a Float as a message
   * @param address The address to send it to
   * @param message The message
   */
  EventBus send(String address, Float message);

  /**
   * Send a Double as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus send(String address, Double message, Handler<Message<T>> replyHandler);

  /**
   * Send a double as a message
   * @param address The address to send it to
   * @param message The message
   * @param timeout - Timeout in ms. If no reply received within the timeout then the reply handler will be unregistered
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus sendWithTimeout(String address, Double message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * Send a Double as a message
   * @param address The address to send it to
   * @param message The message
   */
  EventBus send(String address, Double message);

  /**
   * Send a Boolean as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus send(String address, Boolean message, Handler<Message<T>> replyHandler) ;

  /**
   * Send a boolean as a message
   * @param address The address to send it to
   * @param message The message
   * @param timeout - Timeout in ms. If no reply received within the timeout then the reply handler will be unregistered
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus sendWithTimeout(String address, Boolean message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * Send a Boolean as a message
   * @param address The address to send it to
   * @param message The message
   */
  EventBus send(String address, Boolean message);

  /**
   * Send a Short as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus send(String address, Short message, Handler<Message<T>> replyHandler);


  /**
   * Send a short as a message
   * @param address The address to send it to
   * @param message The message
   * @param timeout - Timeout in ms. If no reply received within the timeout then the reply handler will be unregistered
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus sendWithTimeout(String address, Short message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * Send a Short as a message
   * @param address The address to send it to
   * @param message The message
   */
  EventBus send(String address, Short message);

  /**
   * Send a Character as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus send(String address, Character message, Handler<Message<T>> replyHandler);

  /**
   * Send a character as a message
   * @param address The address to send it to
   * @param message The message
   * @param timeout - Timeout in ms. If no reply received within the timeout then the reply handler will be unregistered
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus sendWithTimeout(String address, Character message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * Send a Character as a message
   * @param address The address to send it to
   * @param message The message
   */
  EventBus send(String address, Character message);

  /**
   * Send a Byte as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus send(String address, Byte message, Handler<Message<T>> replyHandler);

  /**
   * Send a byte as a message
   * @param address The address to send it to
   * @param message The message
   * @param timeout - Timeout in ms. If no reply received within the timeout then the reply handler will be unregistered
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  <T> EventBus sendWithTimeout(String address, Byte message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * Send a Byte as a message
   * @param address The address to send it to
   * @param message The message
   */
  EventBus send(String address, Byte message);

  /**
   * Publish a message
   * @param address The address to publish it to
   * @param message The message
   */
  EventBus publish(String address, Object message);

  /**
   * Publish a JSON object as a message
   * @param address The address to publish it to
   * @param message The message
   */
  EventBus publish(String address, JsonObject message);

  /**
   * Publish a JSON array as a message
   * @param address The address to publish it to
   * @param message The message                S
   */
  EventBus publish(String address, JsonArray message);

  /**
   * Publish a Buffer as a message
   * @param address The address to publish it to
   * @param message The message
   */
  EventBus publish(String address, Buffer message);

  /**
   * Publish a byte[] as a message
   * @param address The address to publish it to
   * @param message The message
   */
  EventBus publish(String address, byte[] message);

  /**
   * Publish a String as a message
   * @param address The address to publish it to
   * @param message The message
   */
  EventBus publish(String address, String message);

  /**
   * Publish an Integer as a message
   * @param address The address to publish it to
   * @param message The message
   */
  EventBus publish(String address, Integer message);

  /**
   * Publish a Long as a message
   * @param address The address to publish it to
   * @param message The message
   */
  EventBus publish(String address, Long message);

  /**
   * Publish a Float as a message
   * @param address The address to publish it to
   * @param message The message
   */
  EventBus publish(String address, Float message);

  /**
   * Publish a Double as a message
   * @param address The address to publish it to
   * @param message The message
   */
  EventBus publish(String address, Double message);

  /**
   * Publish a Boolean as a message
   * @param address The address to publish it to
   * @param message The message
   */
  EventBus publish(String address, Boolean message);

  /**
   * Publish a Short as a message
   * @param address The address to publish it to
   * @param message The message
   */
  EventBus publish(String address, Short message);

  /**
   * Publish a Character as a message
   * @param address The address to publish it to
   * @param message The message
   */
  EventBus publish(String address, Character message);

  /**
   * Publish a Byte as a message
   * @param address The address to publish it to
   * @param message The message
   */
  EventBus publish(String address, Byte message);

  /**
   * Unregisters a handler given the address and the handler
   * @param address The address the handler was registered at
   * @param handler The handler
   * @param resultHandler Optional completion handler. If specified, when the unregister has been
   * propagated to all nodes of the event bus, the handler will be called.
   */
  EventBus unregisterHandler(String address, Handler<? extends Message> handler,
                            Handler<AsyncResult<Void>> resultHandler);

  /**
   * Unregisters a handler given the address and the handler
   * @param address The address the handler was registered at
   * @param handler The handler
   */
  EventBus unregisterHandler(String address, Handler<? extends Message> handler);

  /**
   * Registers a handler against the specified address
   * @param address The address to register it at
   * @param handler The handler
   * @param resultHandler Optional completion handler. If specified, when the register has been
   * propagated to all nodes of the event bus, the handler will be called.
   */
  EventBus registerHandler(String address, Handler<? extends Message> handler,
                           Handler<AsyncResult<Void>> resultHandler);

  /**
   * Registers a handler against the specified address
   * @param address The address to register it at
   * @param handler The handler
   */
  EventBus registerHandler(String address, Handler<? extends Message> handler);

  /**
   * Registers a local handler against the specified address. The handler info won't
   * be propagated across the cluster
   * @param address The address to register it at
   * @param handler The handler
   */
  EventBus registerLocalHandler(String address, Handler<? extends Message> handler);

  /**
   * Sets a default timeout, in ms, for replies. If a messages is sent specify a reply handler
   * but without specifying a timeout, then the reply handler is timed out, i.e. it is automatically unregistered
   * if a message hasn't been received before timeout.
   * The default value for default send timeout is -1, which means "never timeout".
   * @param timeoutMs
   */
  EventBus setDefaultReplyTimeout(long timeoutMs);

  /**
   * Return the value for default send timeout
   */
  long getDefaultReplyTimeout();
}

