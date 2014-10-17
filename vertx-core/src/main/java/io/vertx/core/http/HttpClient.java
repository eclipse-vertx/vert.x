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

package io.vertx.core.http;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;

/**
 * An HTTP client that maintains a pool of connections to a specific host, at a specific port. The client supports
 * pipelining of requests.<p>
 * As well as HTTP requests, the client can act as a factory for {@code WebSocket websockets}.<p>
 * If an instance is instantiated from an event loop then the handlers
 * of the instance will always be called on that same event loop.
 * If an instance is instantiated from some other arbitrary Java thread (i.e. when running embedded) then
 * and event loop will be assigned to the instance and used when any of its handlers
 * are called.<p>
 * Instances of HttpClient are thread-safe.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface HttpClient {

  /**
   * Set an exception handler
   *
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient exceptionHandler(Handler<Throwable> handler);

  HttpClientRequest request(HttpMethod method, String absoluteURI, Handler<HttpClientResponse> responseHandler);

  HttpClientRequest request(HttpMethod method, int port, String host, String requestURI, Handler<HttpClientResponse> responseHandler);


  HttpClient connectWebsocket(int port, String host, String requestURI, Handler<WebSocket> wsConnect);

  HttpClient connectWebsocket(int port, String host, String requestURI, MultiMap headers, Handler<WebSocket> wsConnect);

  HttpClient connectWebsocket(int port, String host, String requestURI, MultiMap headers, WebsocketVersion version,
                              Handler<WebSocket> wsConnect);

  HttpClient connectWebsocket(int port, String host, String requestURI, MultiMap headers, WebsocketVersion version,
                              String subProtocols, Handler<WebSocket> wsConnect);

  /**
   * Close the HTTP client. This will cause any pooled HTTP connections to be closed.
   */
  void close();
}
