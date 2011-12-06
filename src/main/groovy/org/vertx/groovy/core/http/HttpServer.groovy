/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.groovy.core.http

import org.vertx.java.core.Handler

class HttpServer extends org.vertx.java.core.http.HttpServer {

  def requestHandler(Closure hndlr) {
    super.requestHandler(wrapRequestHandler(hndlr))
  }

  def websocketHandler(Closure hndlr) {
    super.websocketHandler(wrapWebsocketHandler(hndlr))
  }

  def close(Closure hndlr) {
    super.close(hndlr as Handler)
  }

  protected wrapRequestHandler(hndlr) {
    return {hndlr.call(new HttpServerRequest(it))} as Handler
  }

  protected wrapWebsocketHandler(hndlr) {
    return {hndlr.call(new WebSocket(it))} as Handler
  }

}
