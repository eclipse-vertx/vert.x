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
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;

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
@VertxGen
public interface EventBus {

	/**
	 * Close the EventBus and release all resources. 
	 * 
	 * @param completionHandler
	 */
  void close(Handler<AsyncResult<Void>> completionHandler);

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   */
  @Fluent
  EventBus send(String address, Object message);

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  @Fluent
  <T> EventBus send(String address, Object message, Handler<Message<T>> replyHandler);

  /**
   * Send an object as a message
   * @param address The address to send it to
   * @param message The message
   * @param timeout - Timeout in ms. If no reply received within the timeout then the reply handler will be unregistered
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  @Fluent
  <T> EventBus sendWithTimeout(String address, Object message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * Publish a message
   * @param address The address to publish it to
   * @param message The message
   */
  @Fluent
  EventBus publish(String address, Object message);

  /**
   * Registers a handler against the specified address
   * @param address The address to register it at
   * @param handler The handler
   * @return the event bus registration
   */
  <T> Registration registerHandler(String address, Handler<Message<T>> handler);

  /**
   * Registers a local handler against the specified address. The handler info won't
   * be propagated across the cluster
   * @param address The address to register it at
   * @param handler The handler
   * @return the event bus registration
   */
  <T> Registration registerLocalHandler(String address, Handler<Message<T>> handler);

  /**
   * Sets a default timeout, in ms, for replies. If a messages is sent specify a reply handler
   * but without specifying a timeout, then the reply handler is timed out, i.e. it is automatically unregistered
   * if a message hasn't been received before timeout.
   * The default value for default send timeout is -1, which means "never timeout".
   * @param timeoutMs
   */
  @Fluent
  EventBus setDefaultReplyTimeout(long timeoutMs);

  /**
   * Return the value for default send timeout
   */
  long getDefaultReplyTimeout();

  @GenIgnore
  <T> EventBus registerCodec(Class<T> type, MessageCodec<T> codec);

  @GenIgnore
  <T> EventBus unregisterCodec(Class<T> type);
}

