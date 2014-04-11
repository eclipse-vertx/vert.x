/*
 * Copyright (c) 2011-2013 The original author or authors
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

package org.vertx.java.core.http;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.ServerSSLSupport;
import org.vertx.java.core.ServerTCPSupport;

import java.util.Set;

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

  /**
   * Set if the {@link HttpServer} should compress the http response if the connected client supports it.
   */
  HttpServer setCompressionSupported(boolean compressionSupported);

  /**
   * Returns {@code true} if the {@link HttpServer} should compress the http response if the connected client supports it.
   */
  boolean isCompressionSupported();

  /**
   * Sets the maximum websocket frame size in bytes. Default is 65536 bytes.
   * @param maxSize The size in bytes
   */
  HttpServer setMaxWebSocketFrameSize(int maxSize);

  /**
   * Get the maximum websocket frame size in bytes.
   */
  int getMaxWebSocketFrameSize();

  /**
   * Set the supported websocket subprotocols. Using null to disable support of subprotocols.
   */
  HttpServer setWebSocketSubProtocols(String... subProtocols);

  /**
   * Returns a immutable {@link Set} which holds all the supported subprotocols. An empty set is returned if
   * non are supported. This is the default.
   */
  Set<String> getWebSocketSubProtocols();
}
