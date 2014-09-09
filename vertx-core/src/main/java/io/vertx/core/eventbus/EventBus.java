/*
 * Copyright 2014 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
 */

package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * A distributed lightweight event bus which can encompass multiple vert.x instances.
 * The event bus implements publish / subscribe, point to point messaging and request-response messaging.<p>
 * Messages sent over the event bus are represented by instances of the {@link io.vertx.core.eventbus.Message} class.<p>
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
  <T> EventBus send(String address, Object message, Handler<AsyncResult<Message<T>>> replyHandler);

  @Fluent
  <T> EventBus send(String address, Object message, DeliveryOptions options);

  @Fluent
  <T> EventBus send(String address, Object message, DeliveryOptions options, Handler<AsyncResult<Message<T>>> replyHandler);

  /**
   * Publish a message
   * @param address The address to publish it to
   * @param message The message
   */
  @Fluent
  EventBus publish(String address, Object message);

  @Fluent
  EventBus publish(String address, Object message, DeliveryOptions options);

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

  @GenIgnore
  EventBus registerCodec(MessageCodec codec);

  @GenIgnore
  EventBus unregisterCodec(String name);

  @GenIgnore
  <T> EventBus registerDefaultCodec(Class<T> clazz, MessageCodec<T, ?> codec);

  @GenIgnore
  EventBus unregisterDefaultCodec(Class clazz);

  @GenIgnore
  <T> T createProxy(Class<T> clazz, String address);

  @GenIgnore
  <T> Registration registerService(T service, String address);

}

