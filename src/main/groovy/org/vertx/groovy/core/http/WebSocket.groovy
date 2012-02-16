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

package org.vertx.groovy.core.http

import org.vertx.java.core.Handler

class WebSocket {

  @Delegate org.vertx.java.core.http.WebSocket wrappedSocket

  WebSocket(webSocket) {
    this.wrappedSocket = webSocket
  }

  def getUri() { return wrappedSocket.uri }

  def dataHandler(Closure hndlr) {
    wrappedSocket.dataHandler(hndlr as Handler)
  }

  def endHandler(Closure hndlr) {
    wrappedSocket.endHandler(hndlr as Handler)
  }

  def exceptionHandler(Closure hndlr) {
    wrappedSocket.exceptionHandler(hndlr as Handler)
  }

  def closedHandler(Closure hndlr) {
    wrappedSocket.closedHandler(hndlr as Handler)
  }
}
