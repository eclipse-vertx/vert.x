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

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.metrics.Measured;
import io.vertx.core.streams.WriteStream;

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
public interface EventBus extends Measured {

  /**
	 * Close the EventBus and release all resources. 
   *
   * @param completionHandler may be {@code null}
   */
  void close(Handler<AsyncResult<Void>> completionHandler);

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message, may be {@code null}
   */
  @Fluent
  EventBus send(String address, Object message);

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message, may be {@code null}
   * @param replyHandler Reply handler will be called when any reply from the recipient is received, may be {@code null}
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
   * @param message The message, may be {@code null}
   */
  @Fluent
  EventBus publish(String address, Object message);

  @Fluent
  EventBus publish(String address, Object message, DeliveryOptions options);

  /**
   * Create a message consumer against the specified address. The returned consumer is not yet registered
   * at the address, registration will be effective when {@link MessageConsumer#handler(io.vertx.core.Handler)}
   * is called.
   *
   * @param address The address that will register it at
   * @return the event bus message consumer
   */
  <T> MessageConsumer<T> consumer(String address);

  /**
   * Create a local message consumer against the specified address. The handler info won't
   * be propagated across the cluster. The returned consumer is not yet registered at the
   * address, registration will be effective when {@link MessageConsumer#handler(io.vertx.core.Handler)}
   * is called.
   *
   * @param address The address to register it at
   * @return the event bus message consumer
   */
  <T> MessageConsumer<T> localConsumer(String address);

  /**
   * Create a message sender against the specified address. The returned sender will invoke the {@link #send(String, Object)}
   * method when the stream {@link io.vertx.core.streams.WriteStream#write(Object)} method is called with the sender
   * address and the provided data.
   *
   * @param address The address to send it to
   * @return The sender
   */
  <T> WriteStream<T> sender(String address);

  /**
   * Create a message sender against the specified address. The returned sender will invoke the {@link #send(String, Object, DeliveryOptions)}
   * method when the stream {@link io.vertx.core.streams.WriteStream#write(Object)} method is called with the sender
   * address, the provided data and the sender delivery options.
   *
   * @param address The address to send it to
   * @return The sender
   */
  <T> WriteStream<T> sender(String address, DeliveryOptions options);

  /**
   * Create a message publisher against the specified address. The returned publisher will invoke the {@link #publish(String, Object)}
   * method when the stream {@link io.vertx.core.streams.WriteStream#write(Object)} method is called with the publisher
   * address and the provided data.
   *
   * @param address The address to publish it to
   * @return The publisher
   */
  <T> WriteStream<T> publisher(String address);

  /**
   * Create a message publisher against the specified address. The returned publisher will invoke the {@link #publish(String, Object, DeliveryOptions)}
   * method when the stream {@link io.vertx.core.streams.WriteStream#write(Object)} method is called with the publisher
   * address, the provided data and the publisher delivery options.
   *
   * @param address The address to publish it to
   * @return The publisher
   */
  <T> WriteStream<T> publisher(String address, DeliveryOptions options);

  @GenIgnore
  EventBus registerCodec(MessageCodec codec);

  @GenIgnore
  EventBus unregisterCodec(String name);

  @GenIgnore
  <T> EventBus registerDefaultCodec(Class<T> clazz, MessageCodec<T, ?> codec);

  @GenIgnore
  EventBus unregisterDefaultCodec(Class clazz);

}

