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

package org.vertx.groovy.core.eventbus

import org.vertx.groovy.core.buffer.Buffer
import org.vertx.java.core.AsyncResultHandler
import org.vertx.java.core.Handler
import org.vertx.java.core.eventbus.EventBus as JEventBus
import org.vertx.java.core.json.JsonObject

import java.util.concurrent.ConcurrentHashMap

/**
 * A distributed lightweight event bus which can encompass multiple vert.x instances.
 * The event bus implements both publish / subscribe network and point to point messaging.<p>
 *
 * Messages sent over the event bus are represented by instances of the {@link Message} class.
 * Subclasses of Message exist for messages that represent all primitive types as well as {@code String},
 * {@link Buffer}, byte[] and {@link JsonObject}<p>
 *
 * For publish / subscribe, messages can be published to an address using one of the {@code publish} methods. An
 * address is a simple {@code String} instance.
 * Handlers are registered against an address. There can be multiple handlers registered against each address, and a particular handler can
 * be registered against multiple addresses. The event bus will route a sent message to all handlers which are
 * registered against that address.<p>
 *
 * For point to point messaging, messages can be sent to an address using one of the {@code send} methods.
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
class EventBus {

  private final JEventBus jEventBus
  
  public EventBus(JEventBus jEventBus) {
    this.jEventBus = jEventBus
  }

  private Map handlerMap = new ConcurrentHashMap()

  /**
   * Send a message on the event bus.
   * Message can be a java.util.Map (Representing a JSON message), a String, boolean,
   * byte, short, int, long, float, double or {@link org.vertx.java.core.buffer.Buffer}
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  void send(String address, message, Closure replyHandler = null) {
    if (message != null) {
      message = convertMessage(message)
      jEventBus.send(address, convertMessage(message), wrapHandler(replyHandler))
    } else {
      // Just choose an overloaded method...
      jEventBus.send(address, (String)null, wrapHandler(replyHandler))
    }
  }

  /**
   * Publish a message on the event bus.
   * Message can be a java.util.Map (Representing a JSON message), a String, boolean,
   * byte, short, int, long, float, double or {@link org.vertx.java.core.buffer.Buffer}
   * @param address The address to publish it to
   * @param message The message
   */
  void publish(String address, message) {
    if (message != null) {
      message = convertMessage(message)
      jEventBus.publish(address, convertMessage(message))
    } else {
      // Just choose an overloaded method...
      jEventBus.publish(address, (String)null)
    }
  }

  /**
   * Registers a handler against the specified address. When a message arrives the handler
   * will receive an instance of {@link Message}
   * @param address The address to register it at
   * @param handler The handler
   * @param resultHandler Optional completion handler. If specified, then when the register has been
   * propagated to all nodes of the event bus, the handler will be called.
   * @return A unique handler id
   */
  String registerHandler(String address, Closure handler, Closure resultHandler = null) {
    def wrapped = wrapHandler(handler)
    handlerMap.put(handler, wrapped)
    jEventBus.registerHandler(address, wrapped, resultHandler as AsyncResultHandler)
  }

  /**
   * Registers a local handler against the specified address. The handler info won't
   * be propagated across the cluster. When a message arrives the handler
   * will receive an instance of {@link Message}
   * @param address The address to register it at
   * @param handler The handler
   * @return A unique handler id
   */
  String registerLocalHandler(String address, Closure handler, Closure resultHandler = null) {
    def wrapped = wrapHandler(handler)
    handlerMap.put(handler, wrapped)
    jEventBus.registerLocalHandler(address, wrapped, resultHandler as AsyncResultHandler)
  }

  /**
   * Registers a handler against a uniquely generated address, the address is returned as the id. When a message arrives the handler
   * will receive an instance of {@link Message}
   * @param handler
   * @param resultHandler Optional result handler. If specified, then when the register has been
   * propagated to all nodes of the event bus, the handler will be called.
   * @return A unique handler id which is the same as the address
   */
  String registerSimpleHandler(handler, Closure resultHandler = null) {
    jEventBus.registerHandler(wrapHandler(handler), resultHandler as AsyncResultHandler)
  }

  /**
   * Unregisters a handler given the address and the handler
   * @param address The address the handler was registered to
   * @param handler The handler
   * @param resultHandler Optional completion handler. If specified, then when the unregister has been
   * propagated to all nodes of the event bus, the handler will be called.
   */
  void unregisterHandler(String address, Closure handler, Closure resultHandler = null) {
    def wrapped = handlerMap.remove(handler)
    if (wrapped != null) {
      jEventBus.unregisterHandler(address, wrapped, resultHandler as AsyncResultHandler)
    }
  }

  /**
   * Unregister a handler given the unique handler id
   * @param id The handler id
   * @param resultHandler Optional completion handler. If specified, then when the unregister has been
   * propagated to all nodes of the event bus, the handler will be called.
   */
  void unregisterSimpleHandler(String id, Closure resultHandler = null) {
    jEventBus.unregisterHandler(id, resultHandler as AsyncResultHandler)
  }

  protected static convertMessage(message) {
    if (message instanceof Map) {
      message = new JsonObject(message)
    } else if (message instanceof Buffer) {
      message = ((Buffer)message).toJavaBuffer()
    }
    message
  }

  protected static wrapHandler(replyHandler) {
    if (replyHandler != null) {
      def wrapped = { replyHandler(new Message(it)) } as Handler
      return wrapped
    } else {
      return null
    }
  }
}
