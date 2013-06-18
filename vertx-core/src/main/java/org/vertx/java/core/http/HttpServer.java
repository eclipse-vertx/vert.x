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

package org.vertx.java.core.http;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.ServerSSLSupport;
import org.vertx.java.core.ServerTCPSupport;

/**
 * An HTTP and WebSockets server<p>
 * If an instance is instantiated from an event loop then the handlers
 * of the instance will always be called on that same event loop.
 * If an instance is instantiated from some other arbitrary Java thread then
 * an event loop will be assigned to the instance and used when any of its handlers
 * are called.<p>
 * Instances of HttpServer are thread-safe.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface HttpServer extends ServerSSLSupport<HttpServer>, ServerTCPSupport<HttpServer> {

  /**
   * Set the request handler for the server to {@code requestHandler}. As HTTP requests are received by the server,
   * instances of {@link HttpServerRequest} will be created and passed to this handler.
   *
   * @return a reference to this, so methods can be chained.
   */
  HttpServer requestHandler(Handler<HttpServerRequest> requestHandler);

  /**
   * Get the request handler
   * @return The request handler
   */
  Handler<HttpServerRequest> requestHandler();

  /**
   * Set the websocket handler for the server to {@code wsHandler}. If a websocket connect handshake is successful a
   * new {@link ServerWebSocket} instance will be created and passed to the handler.
   *
   * @return a reference to this, so methods can be chained.
   */
  HttpServer websocketHandler(Handler<ServerWebSocket> wsHandler);

  /**
   * Get the websocket handler
   * @return The websocket handler
   */
  Handler<ServerWebSocket> websocketHandler();


  /**
   * Tell the server to start listening on all available interfaces and port {@code port}. Be aware this is an
   * async operation and the server may not bound on return of the method.
   */
  HttpServer listen(int port);

  /**
   * Tell the server to start listening on all available interfaces and port {@code port}
   *
   */
  HttpServer listen(int port, Handler<AsyncResult<HttpServer>> listenHandler);

  /**
   * Tell the server to start listening on port {@code port} and hostname or ip address given by {@code host}. Be aware this is an
   * async operation and the server may not bound on return of the method.
   *
   */
  HttpServer listen(int port, String host);

  /**
   * Tell the server to start listening on port {@code port} and hostname or ip address given by {@code host}.
   *
   */
  HttpServer listen(int port, String host, Handler<AsyncResult<HttpServer>> listenHandler);
  
  /**
   * Close the server. Any open HTTP connections will be closed.
   */
  void close();

  /**
   * Close the server. Any open HTTP connections will be closed. The {@code doneHandler} will be called when the close
   * is complete.
   */
  void close(Handler<AsyncResult<Void>> doneHandler);
}
