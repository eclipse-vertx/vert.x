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

  private org.vertx.java.core.eventbus.EventBus jEB() {
    org.vertx.java.core.eventbus.EventBus.instance
  }

  void send(String address, message, replyHandler = null) {
    jEB().send(address, message, replyHandler as Handler)
  }

  String registerHandler(String address, handler, resultHandler = null) {
    jEB().registerHandler(address, handler as Handler, resultHandler as Handler)
  }

  String registerLocalHandler(String address, handler, resultHandler = null) {
    jEB().registerLocalHandler(address, handler as Handler, resultHandler as Handler)
  }

  String registerSimpleHandler(handler, resultHandler = null) {
    jEB().registerHandler(handler as Handler, resultHandler as Handler)
  }

  void unregisterHandler(String address, handler, resultHandler = null) {
    jEB().unregisterHandler(address, handler as Handler, resultHandler as Handler)
  }

  void unregisterSimpleHandler(String id, resultHandler = null) {
    jEB().unregisterHandler(id, resultHandler as Handler)
  }

}
