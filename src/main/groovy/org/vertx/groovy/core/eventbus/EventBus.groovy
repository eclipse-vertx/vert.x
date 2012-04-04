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

import org.vertx.java.core.eventbus.EventBus as JEventBus

import java.util.concurrent.ConcurrentHashMap
import org.vertx.groovy.core.buffer.Buffer
import org.vertx.java.core.AsyncResultHandler
import org.vertx.java.core.Handler
import org.vertx.java.core.json.JsonObject

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class EventBus {

  
  private final JEventBus jEventBus;
  
  public EventBus(JEventBus jEventBus) {
    this.jEventBus = jEventBus;  
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
   * Registers a handler against the specified address
   * @param address The address to register it at
   * @param handler The handler
   * @param resultHandler Optional completion handler. If specified, then when the register has been
   * propagated to all nodes of the event bus, the handler will be called.
   * @return The handler id which is the same as the address
   */
  String registerHandler(String address, Closure handler, Closure resultHandler = null) {
    def wrapped = wrapHandler(handler)
    handlerMap.put(handler, wrapped)
    jEventBus.registerHandler(address, wrapped, resultHandler as AsyncResultHandler)
  }

  /**
   * Registers a local handler against the specified address. The handler info won't
   * be propagated across the cluster
   * @param address The address to register it at
   * @param handler The handler
   * @return The handler id which is the same as the address
   */
  String registerLocalHandler(String address, Closure handler, Closure resultHandler = null) {
    def wrapped = wrapHandler(handler)
    handlerMap.put(handler, wrapped)
    jEventBus.registerLocalHandler(address, wrapped, resultHandler as AsyncResultHandler)
  }

  /**
   * Registers a handler against a uniquely generated address, the address is returned as the id
   * @param handler
   * @param resultHandler Optional result handler. If specified, then when the register has been
   * propagated to all nodes of the event bus, the handler will be called.
   * @return The handler id which is the same as the address
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
      return null;
    }
  }




}

