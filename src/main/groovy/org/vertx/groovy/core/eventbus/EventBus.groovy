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

import org.vertx.java.core.Handler
import org.vertx.java.core.json.JsonObject
import org.vertx.java.core.AsyncResultHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class EventBus {

  static EventBus instance = new EventBus()

  static void setClustered(String hostname) {
    org.vertx.java.core.eventbus.EventBus.setClustered(hostname)
  }

  static void setClustered(int port, String hostname) {
    org.vertx.java.core.eventbus.EventBus.setClustered(port, hostname)
  }

  static void setClustered(int port, String hostname, String clusterProviderClassName) {
    org.vertx.java.core.eventbus.EventBus.setClustered(port, hostname, clusterProviderClassName)
  }

  private Map handlerMap = new ConcurrentHashMap()

  private org.vertx.java.core.eventbus.EventBus jEB() {
    org.vertx.java.core.eventbus.EventBus.instance
  }

  private def convertMessage(message) {
    if (message instanceof Map) {
      message = new JsonObject(message)
    }
    message
  }

  private def wrapHandler(replyHandler) {
    if (replyHandler != null) {
      def wrapped = { replyHandler.call(new Message(it)) } as Handler
      return wrapped
    } else {
      return null;
    }
  }

  void send(String address, message, replyHandler = null) {
    if (message != null) {
      message = convertMessage(message)
      jEB().send(address, convertMessage(message), wrapHandler(replyHandler))
    } else {
      // Just choose an overloaded method...
      jEB().send(address, (String)null, wrapHandler(replyHandler))
    }
  }

  String registerHandler(String address, handler, resultHandler = null) {
    def wrapped = wrapHandler(handler)
    handlerMap.put(handler, wrapped)
    jEB().registerHandler(address, wrapped, resultHandler as AsyncResultHandler)
  }

  String registerLocalHandler(String address, handler, resultHandler = null) {
    def wrapped = wrapHandler(handler)
    handlerMap.put(handler, wrapped)
    jEB().registerLocalHandler(address, wrapped, resultHandler as AsyncResultHandler)
  }

  String registerSimpleHandler(handler, resultHandler = null) {
    jEB().registerHandler(wrapHandler(handler), resultHandler as AsyncResultHandler)
  }

  void unregisterHandler(String address, handler, resultHandler = null) {
    def wrapped = handlerMap.remove(handler)
    if (wrapped != null) {
      jEB().unregisterHandler(address, wrapped, resultHandler as AsyncResultHandler)
    }
  }

  void unregisterSimpleHandler(String id, resultHandler = null) {
    jEB().unregisterHandler(id, resultHandler as AsyncResultHandler)
  }

  class Message {

    def body

    private org.vertx.java.core.eventbus.Message jMessage;

    private Message(org.vertx.java.core.eventbus.Message jMessage) {
      if (jMessage.body instanceof JsonObject) {
        this.body = jMessage.body.toMap()
      } else {
        this.body = jMessage.body
      }
      this.jMessage = jMessage
    }

    def reply(message, replyHandler = null) {
      message = convertMessage(message)
      jMessage.reply(message, wrapHandler(replyHandler))
    }
  }

}

