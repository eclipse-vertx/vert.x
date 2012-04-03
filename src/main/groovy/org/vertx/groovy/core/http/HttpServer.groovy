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
import org.vertx.java.core.impl.VertxInternal

/**
 * An HTTP and WebSockets server
 * <p>
 * This class is a thread safe and can safely be used by different threads.
 * <p>
 * If an instance is instantiated from an event loop then the handlers
 * of the instance will always be called on that same event loop.
 * If an instance is instantiated from some other arbitrary Java thread then
 * and event loop will be assigned to the instance and used when any of its handlers
 * are called.
 * <p>
 * Instances cannot be used from worker verticles
 * @author Peter Ledbrook
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class HttpServer extends org.vertx.java.core.http.HttpServer {

  private reqHandler;
  private wsHandler;

  public HttpServer(VertxInternal vertx) {
    super(vertx);
  }

  /**
   * Set the request handler for the server to {@code requestHandler}. As HTTP requests are received by the server,
   * instances of {@link HttpServerRequest} will be created and passed to this handler.
   *
   * @return a reference to this, so methods can be chained.
   */
  HttpServer requestHandler(Closure hndlr) {
    super.requestHandler(wrapRequestHandler(hndlr))
    this.reqHandler = hndlr
    this
  }

  /**
   * Get the request handler
   * @return The request handler
   */
  Closure getRequestHandler() {
    return reqHandler;
  }

  /**
   * Set the websocket handler for the server to {@code wsHandler}. If a websocket connect handshake is successful a
   * new {@link WebSocket} instance will be created and passed to the handler.
   *
   * @return a reference to this, so methods can be chained.
   */
  HttpServer websocketHandler(Closure hndlr) {
    super.websocketHandler(wrapWebsocketHandler(hndlr))
    this.wsHandler = hndlr
    this
  }

  /**
   * Get the websocket handler
   * @return The websocket handler
   */
  Closure getWebsocketHandler() {
    wsHandler;
  }

  /**
   * Close the server. Any open HTTP connections will be closed. {@code hndlr} will be called when the close
   * is complete.
   */
  void close(Closure hndlr) {
    super.close(hndlr as Handler)
  }

  private wrapRequestHandler(Closure hndlr) {
    return {hndlr(new HttpServerRequest(it))} as Handler
  }

  private wrapWebsocketHandler(Closure hndlr) {
    return {hndlr(new ServerWebSocket(it))} as Handler
  }

}
