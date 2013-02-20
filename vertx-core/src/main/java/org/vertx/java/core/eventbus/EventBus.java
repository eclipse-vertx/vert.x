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

import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

/**
 * A distributed lightweight event bus which can encompass multiple vert.x instances.
 * The event bus implements both publish / subscribe network and point to point messaging.<p>
 *
 * Messages sent over the event bus are represented by instances of the {@link Message} class.
 * Subclasses of Message exist for messages that represent all primitive types as well as {@code String},
 * {@link Buffer}, byte[] and {@link JsonObject}<p>
 *
 * For publish / subscribe, messages can be published to an address using one of the {@link #publish} methods. An
 * address is a simple {@code String} instance.
 * Handlers are registered against an address. There can be multiple handlers registered against each address, and a particular handler can
 * be registered against multiple addresses. The event bus will route a sent message to all handlers which are
 * registered against that address.<p>
 *
 * For point to point messaging, messages can be sent to an address using one of the {@link #send} methods.
 * The messages will be delivered to a single handler, if one is registered on that address. If more than one
 * handler is registered on the same address, Vert.x will choose one and deliver the message to that. Vert.x will
 * aim to fairly distribute messages in a round-robin way, but does not guarantee strict round-robin under all
 * circumstances.<p>
 *
 * All messages sent over the bus are transient. On event of failure of all or part of the event bus messages
 * may be lost. Applications should be coded to cope with lost messages, e.g. by resending them, and making application
 * services idempotent.<p>
 *
 * The order of messages received by any specific handler from a specific sender should match the order of messages
 * sent from that sender.<p>
 *
 * When sending a message, a reply handler can be provided. If so, it will be called when the reply from the receiver
 * has been received. Reply messages can also be replied to, etc, ad infinitum<p>
 *
 * Different event bus instances can be clustered together over a network, to give a single logical event bus.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface EventBus {

	/**
	 * Close the EventBus and release all resources. 
	 * 
	 * @param doneHandler
	 */
  void close(Handler<Void> doneHandler);

  /**
   * Send a JSON object as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  void send(String address, JsonObject message, Handler<Message<JsonObject>> replyHandler);

  /**
   * Send a JSON object as a message
   * @param address The address to send it to
   * @param message The message
   */
  void send(String address, JsonObject message);

  /**
   * Send a JSON array as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  void send(String address, JsonArray message, Handler<Message<JsonArray>> replyHandler);

  /**
   * Send a JSON array as a message
   * @param address The address to send it to
   * @param message The message
   */
  void send(String address, JsonArray message);

  /**
   * Send a Buffer as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  void send(String address, Buffer message, Handler<Message<Buffer>> replyHandler);

  /**
   * Send a Buffer as a message
   * @param address The address to send it to
   * @param message The message
   */
  void send(String address, Buffer message);

  /**
   * Send a byte[] as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  void send(String address, byte[] message, Handler<Message<byte[]>> replyHandler);

  /**
   * Send a byte[] as a message
   * @param address The address to send it to
   * @param message The message
   */
  void send(String address, byte[] message);

  /**
   * Send a String as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  void send(String address, String message, Handler<Message<String>> replyHandler);

  /**
   * Send a String as a message
   * @param address The address to send it to
   * @param message The message
   */
  void send(String address, String message);

  /**
   * Send an Integer as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  void send(String address, Integer message, Handler<Message<Integer>> replyHandler);

  /**
   * Send an Integer as a message
   * @param address The address to send it to
   * @param message The message
   */
  void send(String address, Integer message);

  /**
   * Send a Long as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  void send(String address, Long message, Handler<Message<Long>> replyHandler);

  /**
   * Send a Long as a message
   * @param address The address to send it to
   * @param message The message
   */
  void send(String address, Long message);

  /**
   * Send a Float as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  void send(String address, Float message, Handler<Message<Float>> replyHandler);

  /**
   * Send a Float as a message
   * @param address The address to send it to
   * @param message The message
   */
  void send(String address, Float message);

  /**
   * Send a Double as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  void send(String address, Double message, Handler<Message<Double>> replyHandler);

  /**
   * Send a Double as a message
   * @param address The address to send it to
   * @param message The message
   */
  void send(String address, Double message);

  /**
   * Send a Boolean as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  void send(String address, Boolean message, Handler<Message<Boolean>> replyHandler) ;

  /**
   * Send a Boolean as a message
   * @param address The address to send it to
   * @param message The message
   */
  void send(String address, Boolean message);

  /**
   * Send a Short as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  void send(String address, Short message, Handler<Message<Short>> replyHandler);

  /**
   * Send a Short as a message
   * @param address The address to send it to
   * @param message The message
   */
  void send(String address, Short message);

  /**
   * Send a Character as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  void send(String address, Character message, Handler<Message<Character>> replyHandler);

  /**
   * Send a Character as a message
   * @param address The address to send it to
   * @param message The message
   */
  void send(String address, Character message);

  /**
   * Send a Byte as a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  void send(String address, Byte message, Handler<Message<Byte>> replyHandler);

  /**
   * Send a Byte as a message
   * @param address The address to send it to
   * @param message The message
   */
  void send(String address, Byte message);
  
  /**
   * Publish a JSON object as a message
   * @param address The address to publish it to
   * @param message The message
   */
  void publish(String address, JsonObject message);

  /**
   * Publish a JSON array as a message
   * @param address The address to publish it to
   * @param message The message
   */
  void publish(String address, JsonArray message);

  /**
   * Publish a Buffer as a message
   * @param address The address to publish it to
   * @param message The message
   */
  void publish(String address, Buffer message);

  /**
   * Publish a byte[] as a message
   * @param address The address to publish it to
   * @param message The message
   */
  void publish(String address, byte[] message);

  /**
   * Publish a String as a message
   * @param address The address to publish it to
   * @param message The message
   */
  void publish(String address, String message);

  /**
   * Publish an Integer as a message
   * @param address The address to publish it to
   * @param message The message
   */
  void publish(String address, Integer message);

  /**
   * Publish a Long as a message
   * @param address The address to publish it to
   * @param message The message
   */
  void publish(String address, Long message);

  /**
   * Publish a Float as a message
   * @param address The address to publish it to
   * @param message The message
   */
  void publish(String address, Float message);

  /**
   * Publish a Double as a message
   * @param address The address to publish it to
   * @param message The message
   */
  void publish(String address, Double message);

  /**
   * Publish a Boolean as a message
   * @param address The address to publish it to
   * @param message The message
   */
  void publish(String address, Boolean message);

  /**
   * Publish a Short as a message
   * @param address The address to publish it to
   * @param message The message
   */
  void publish(String address, Short message);

  /**
   * Publish a Character as a message
   * @param address The address to publish it to
   * @param message The message
   */
  void publish(String address, Character message);

  /**
   * Publish a Byte as a message
   * @param address The address to publish it to
   * @param message The message
   */
  void publish(String address, Byte message);

  /**
   * Unregisters a handler given the address and the handler
   * @param address The address the handler was registered at
   * @param handler The handler
   * @param resultHandler Optional completion handler. If specified, when the unregister has been
   * propagated to all nodes of the event bus, the handler will be called.
   */
  void unregisterHandler(String address, @SuppressWarnings("rawtypes") Handler<? extends Message> handler,
                         AsyncResultHandler<Void> resultHandler);

  /**
   * Unregisters a handler given the address and the handler
   * @param address The address the handler was registered at
   * @param handler The handler
   */
  void unregisterHandler(String address, @SuppressWarnings("rawtypes") Handler<? extends Message> handler);

  /**
   * Registers a handler against the specified address
   * @param address The address to register it at
   * @param handler The handler
   * @param resultHandler Optional completion handler. If specified, when the register has been
   * propagated to all nodes of the event bus, the handler will be called.
   */
  void registerHandler(String address, @SuppressWarnings("rawtypes") Handler<? extends Message> handler,
                       AsyncResultHandler<Void> resultHandler);

  /**
   * Registers a handler against the specified address
   * @param address The address to register it at
   * @param handler The handler
   */
  void registerHandler(String address, @SuppressWarnings("rawtypes") Handler<? extends Message> handler);

  /**
   * Registers a local handler against the specified address. The handler info won't
   * be propagated across the cluster
   * @param address The address to register it at
   * @param handler The handler
   */
  void registerLocalHandler(String address, @SuppressWarnings("rawtypes") Handler<? extends Message> handler);
}

